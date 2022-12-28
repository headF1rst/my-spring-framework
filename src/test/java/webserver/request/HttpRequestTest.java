package webserver.request;

import http.request.HttpRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StopWatch;
import org.springframework.web.client.RestTemplate;
import webserver.ExecutorsTest;

import java.io.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertAll;

@DisplayName("HTTP 요청 테스트")
public class HttpRequestTest {
    private static final Logger logger = LoggerFactory.getLogger(ExecutorsTest.class);

    private String testDirectory = "./src/test/resources/";

    @DisplayName("GET Http Request 생성")
    @Test
    void request_GET() throws IOException {
        InputStream in = new FileInputStream(new File(testDirectory + "Http_GET.txt"));
        HttpRequest request = HttpRequest.from(in);
        assertAll(
                () -> assertThat(request.getHttpMethod()).isEqualTo("GET"),
                () -> assertThat(request.getPath()).isEqualTo("/user/create"),
                () -> assertThat(request.getRequestHeader("Connection")).isEqualTo("keep-alive"),
                () -> assertThat(request.getParameter("userId")).isEqualTo("javajigi")
        );
    }

    @DisplayName("Http Request의 header와 requestLine은 공백이어선 안된다.")
    @Test
    void http_request_empty() throws FileNotFoundException {
        InputStream in = new FileInputStream(new File(testDirectory + "Http_GET_FAIL.txt"));
        assertThatThrownBy(
                () -> HttpRequest.from(in))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @DisplayName("Content-Length 확인")
    @Test
    void check_content_length() throws IOException {
        InputStream in = new FileInputStream(new File(testDirectory + "Http_POST.txt"));
        HttpRequest request = HttpRequest.from(in);
        assertThat(request.getContentLength()).isEqualTo(59);
    }

    @DisplayName("POST Http Request 생성")
    @Test
    void request_POST() throws IOException {
        InputStream in = new FileInputStream(new File(testDirectory + "Http_POST.txt"));
        HttpRequest request = HttpRequest.from(in);
        assertAll(
                () -> assertThat(request.getHttpMethod()).isEqualTo("POST"),
                () -> assertThat(request.getPath()).isEqualTo("/user/create"),
                () -> assertThat(request.getRequestHeader("Connection")).isEqualTo("keep-alive"),
                () -> assertThat(request.getBody("userId").get()).isEqualTo("javajigi")
        );
    }

    @Test
    void request_resttemplate() {
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<String> response = restTemplate.getForEntity("http://localhost:8080", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @DisplayName("서버의 최대 Thread Pool 수만큼 요청을 보낸다.")
    @Test
    void request_maximum_threads() throws InterruptedException {
        final int threadPoolSize = 350;
        final int requestCount = 300;
        ExecutorService executorService = Executors.newFixedThreadPool(threadPoolSize);
        CountDownLatch latch = new CountDownLatch(requestCount);
        RestTemplate restTemplate = new RestTemplate();

        for (int count = 0; count < requestCount; count++) {
            executorService.execute(() -> {
                restTemplate.getForEntity("http://localhost:8080/index.html", String.class);
                latch.countDown();
            });
        }
        boolean await = latch.await(10000, TimeUnit.MILLISECONDS);

        assertAll(
                () -> assertThat(latch.getCount()).isZero(),
                () -> assertThat(await).isTrue()
        );
    }

    @DisplayName("서버의 최대 Thread Pool 수보다 더 많은 요청을 보낸다.")
    @Test
    void request_more_than_maximum_threads() throws InterruptedException {
        final int threadPoolSize = 350;
        final int requestCount = 500;
        ExecutorService executorService = Executors.newFixedThreadPool(threadPoolSize);
        CountDownLatch latch = new CountDownLatch(requestCount);
        RestTemplate restTemplate = new RestTemplate();

        for (int count = 0; count < requestCount; count++) {
            executorService.execute(() -> {
                restTemplate.getForEntity("http://localhost:8080/index.html", String.class);
                latch.countDown();
            });
        }
        boolean await = latch.await(10000, TimeUnit.MILLISECONDS);

        assertAll(
                () -> assertThat(latch.getCount()).isNotZero(),
                () -> assertThat(await).isFalse()
        );
    }

    @DisplayName("서버가 수용하는 최대 스레드풀 이하로 요청을 보내본다. ")
    @Test
    void requestUnderMaxThreadSize() throws InterruptedException {
        int threadPoolSize = 200;
        int requestCount = 100;
        CountDownLatch countDownLatch = new CountDownLatch(requestCount);
        ExecutorService executorService = Executors.newFixedThreadPool(threadPoolSize);
        StopWatch stopWatch = new StopWatch();

        stopWatch.start();
        IntStream.range(0, requestCount).parallel()
                .forEach(n -> executorService.execute(() -> {
                    RestTemplate restTemplate = new RestTemplate();
                    ResponseEntity<String> response =
                            restTemplate.getForEntity("http://localhost:8080/index.html", String.class);
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    logger.debug("Thread - {}", Thread.currentThread());
                    countDownLatch.countDown();
                }
        ));
        boolean await = countDownLatch.await(10000, TimeUnit.MILLISECONDS);
        stopWatch.stop();

        System.out.println("stopWatch.getTotalTimeSeconds() = " + stopWatch.getTotalTimeSeconds());
        assertAll(
                () -> assertThat(countDownLatch.getCount()).isZero(),
                () -> assertThat(await).isTrue()
        );
    }

    @DisplayName("서버가 수용하는 최대 스레드풀 이상으로 요청을 보내본다. ")
    @Test
    void requestOverMaxThreadSize() throws InterruptedException {
        int threadPoolSize = 200;
        int requestCount = 400;
        CountDownLatch countDownLatch = new CountDownLatch(requestCount);
        ExecutorService executorService = Executors.newFixedThreadPool(threadPoolSize);
        StopWatch stopWatch = new StopWatch();

        stopWatch.start();
        IntStream.range(0, requestCount).parallel()
                .forEach(n -> executorService.execute(() -> {
                            RestTemplate restTemplate = new RestTemplate();
                            ResponseEntity<String> response =
                                    restTemplate.getForEntity("http://localhost:8080/index.html", String.class);
                            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                            logger.debug("Thread - {}", Thread.currentThread());
                            countDownLatch.countDown();
                        }
                ));
        boolean await = countDownLatch.await(10000, TimeUnit.MILLISECONDS);
        stopWatch.stop();

        System.out.println("stopWatch.getTotalTimeSeconds() = " + stopWatch.getTotalTimeSeconds());
        assertAll(
                () -> assertThat(countDownLatch.getCount()).isZero(),
                () -> assertThat(await).isTrue()
        );
    }
}
