package org.jetbrains.intellij.build.io

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.nio.file.StandardOpenOption.WRITE
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds

@Timeout(20)
class HttpRequestTest {
  @TempDir
  lateinit var tempDir: Path

  @Test
  fun `the client uses virtual workers and closes its resources`() {
    lateinit var client: HttpClient
    lateinit var executor: ExecutorService
    HttpTestServer { exchange ->
      assertThat(exchange.requestHeaders.getFirst("Accept-Encoding")).isEqualTo("identity")
      exchange.respond("response")
    }.use { server ->
      withHttpClient(connectTimeout = 5.seconds) {
        client = it
        executor = it.executor().orElseThrow() as ExecutorService
        val virtual = CompletableFuture<Boolean>()
        executor.execute { virtual.complete(Thread.currentThread().isVirtual) }
        assertThat(virtual.get(5, TimeUnit.SECONDS)).isTrue()
        val response = sendHttpRequest(it, HttpRequest.newBuilder(server.uri).build(), 5.seconds)
        assertThat(response.body()).isEqualTo("response")
      }
    }
    assertThat(client.isTerminated).isTrue()
    assertThat(executor.isTerminated).isTrue()
  }

  @Test
  fun `an action failure closes the client and executor`() {
    lateinit var client: HttpClient
    lateinit var executor: ExecutorService
    val failure = IOException("The action failed")
    assertThat(assertThrows<IOException> {
      withHttpClient(connectTimeout = 5.seconds) {
        client = it
        executor = it.executor().orElseThrow() as ExecutorService
        throw failure
      }
    }).isSameAs(failure)
    assertThat(client.isTerminated).isTrue()
    assertThat(executor.isTerminated).isTrue()
  }

  @Test
  fun `a deadline includes the response body`() {
    val bodyStarted = CountDownLatch(1)
    lateinit var client: HttpClient
    HttpTestServer { exchange ->
      exchange.sendResponseHeaders(200, 100)
      exchange.responseBody.write(0)
      exchange.responseBody.flush()
      bodyStarted.countDown()
      CountDownLatch(1).await()
    }.use { server ->
      assertThrows<TimeoutException> {
        withHttpClient(connectTimeout = 5.seconds) {
          client = it
          sendHttpRequest(it, HttpRequest.newBuilder(server.uri).build(), 1.seconds)
        }
      }
      assertThat(bodyStarted.count).isZero()
      assertThat(client.isTerminated).isTrue()
      assertThat((client.executor().orElseThrow() as ExecutorService).isTerminated).isTrue()
    }
  }

  @Test
  fun `a deadline cancels a stalled file upload`() {
    val file = tempDir.resolve("upload.bin")
    Files.newByteChannel(file, CREATE_NEW, WRITE).use {
      it.position(64L * 1024 * 1024 - 1)
      it.write(ByteBuffer.wrap(byteArrayOf(0)))
    }
    val requestStarted = CountDownLatch(1)
    lateinit var client: HttpClient
    HttpTestServer {
      requestStarted.countDown()
      CountDownLatch(1).await()
    }.use { server ->
      assertThrows<TimeoutException> {
        withHttpClient(connectTimeout = 5.seconds) {
          client = it
          val request = HttpRequest.newBuilder(server.uri).POST(HttpRequest.BodyPublishers.ofFile(file)).build()
          sendHttpRequest(it, request, 1.seconds)
        }
      }
      assertThat(requestStarted.count).isZero()
      assertThat(client.isTerminated).isTrue()
      assertThat((client.executor().orElseThrow() as ExecutorService).isTerminated).isTrue()
    }
    Files.delete(file)
  }

  @Test
  fun `caller interruption closes the client and preserves interruption`() {
    val requestStarted = CountDownLatch(1)
    val outcome = CompletableFuture<Throwable>()
    val interrupted = AtomicBoolean()
    lateinit var client: HttpClient
    HttpTestServer {
      requestStarted.countDown()
      CountDownLatch(1).await()
    }.use { server ->
      val caller = Thread.ofVirtual().start {
        try {
          withHttpClient(connectTimeout = 5.seconds) {
            client = it
            sendHttpRequest(it, HttpRequest.newBuilder(server.uri).build(), 10.seconds)
          }
          outcome.complete(AssertionError("The request returned"))
        }
        catch (failure: Throwable) {
          interrupted.set(Thread.currentThread().isInterrupted)
          outcome.complete(failure)
        }
      }
      try {
        assertThat(requestStarted.await(5, TimeUnit.SECONDS)).isTrue()
        caller.interrupt()
        assertThat(outcome.get(5, TimeUnit.SECONDS)).isInstanceOf(InterruptedException::class.java)
        caller.join(5000)
        assertThat(caller.isAlive).isFalse()
        assertThat(interrupted).isTrue()
        assertThat(client.isTerminated).isTrue()
        assertThat((client.executor().orElseThrow() as ExecutorService).isTerminated).isTrue()
      }
      finally {
        caller.interrupt()
        caller.join(5000)
      }
    }
  }

  @Test
  fun `an interrupted caller does not start a request`() {
    val requests = AtomicInteger()
    HttpTestServer { exchange ->
      requests.incrementAndGet()
      exchange.respond("unexpected")
    }.use { server ->
      withHttpClient(connectTimeout = 5.seconds) { client ->
        Thread.currentThread().interrupt()
        try {
          assertThrows<InterruptedException> {
            sendHttpRequest(client, HttpRequest.newBuilder(server.uri).build(), 5.seconds)
          }
          assertThat(Thread.currentThread().isInterrupted).isTrue()
        }
        finally {
          Thread.interrupted()
        }
      }
    }
    assertThat(requests).hasValue(0)
  }

  @Test
  fun `a server error does not retry a POST`() {
    val requests = AtomicInteger()
    HttpTestServer { exchange ->
      requests.incrementAndGet()
      exchange.requestBody.readAllBytes()
      exchange.responseHeaders.set("Retry-After", "0")
      exchange.respond("failed", 503)
    }.use { server ->
      withHttpClient(connectTimeout = 5.seconds) { client ->
        val request = HttpRequest.newBuilder(server.uri).POST(HttpRequest.BodyPublishers.ofString("upload")).build()
        val response = sendHttpRequest(client, request, 5.seconds)
        assertThat(response.statusCode()).isEqualTo(503)
        assertThat(response.body()).isEqualTo("failed")
      }
    }
    assertThat(requests).hasValue(1)
  }

  @Test
  fun `a disconnect does not retry a POST`() {
    val requests = AtomicInteger()
    HttpTestServer { exchange ->
      requests.incrementAndGet()
      exchange.requestBody.readAllBytes()
    }.use { server ->
      assertThrows<IOException> {
        withHttpClient(connectTimeout = 5.seconds) { client ->
          val request = HttpRequest.newBuilder(server.uri).POST(HttpRequest.BodyPublishers.ofString("upload")).build()
          sendHttpRequest(client, request, 5.seconds)
        }
      }
    }
    assertThat(requests).hasValue(1)
  }

  @Test
  fun `a temporary redirect preserves the file upload`() {
    val contents = "file contents".toByteArray()
    val file = Files.write(tempDir.resolve("redirect.bin"), contents)
    val requests = AtomicInteger()
    HttpTestServer { exchange ->
      requests.incrementAndGet()
      assertThat(exchange.requestMethod).isEqualTo("POST")
      assertThat(exchange.requestBody.readAllBytes()).isEqualTo(contents)
      if (exchange.requestURI.path == "/target") {
        exchange.respond("uploaded")
      }
      else {
        exchange.responseHeaders.set("Location", "/target")
        exchange.respond("", 307)
      }
    }.use { server ->
      withHttpClient(connectTimeout = 5.seconds) { client ->
        val request = HttpRequest.newBuilder(server.uri).POST(HttpRequest.BodyPublishers.ofFile(file)).build()
        assertThat(sendHttpRequest(client, request, 5.seconds).body()).isEqualTo("uploaded")
      }
    }
    assertThat(requests).hasValue(2)
    Files.delete(file)
  }

  @Test
  fun `a redirect preserves credentials within the same origin`() {
    val requests = AtomicInteger()
    HttpTestServer { exchange ->
      requests.incrementAndGet()
      assertThat(exchange.requestHeaders.getFirst("Authorization")).isEqualTo("Bearer secret")
      if (exchange.requestURI.path == "/target") {
        exchange.respond("redirected")
      }
      else {
        exchange.responseHeaders.set("Location", "/target")
        exchange.respond("", 302)
      }
    }.use { server ->
      withHttpClient(connectTimeout = 5.seconds) { client ->
        val request = HttpRequest.newBuilder(server.uri).header("Authorization", "Bearer secret").build()
        assertThat(sendHttpRequest(client, request, 5.seconds).body()).isEqualTo("redirected")
      }
    }
    assertThat(requests).hasValue(2)
  }

  @Test
  fun `a redirect does not forward credentials to another origin`() {
    val authorization = CompletableFuture<String>()
    HttpTestServer { exchange ->
      authorization.complete(exchange.requestHeaders.getFirst("Authorization") ?: "absent")
      exchange.respond("redirected")
    }.use { target ->
      HttpTestServer { exchange ->
        exchange.responseHeaders.set("Location", target.uri.toString())
        exchange.respond("", 302)
      }.use { source ->
        withHttpClient(connectTimeout = 5.seconds) { client ->
          val request = HttpRequest.newBuilder(source.uri).header("Authorization", "Bearer secret").build()
          assertThat(sendHttpRequest(client, request, 5.seconds).body()).isEqualTo("redirected")
        }
      }
    }
    assertThat(authorization.get(5, TimeUnit.SECONDS)).isEqualTo("absent")
  }
}
