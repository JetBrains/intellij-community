package com.intellij.platform.buildScripts.downloader

import com.intellij.platform.buildScripts.concurrency.taskScope
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.intellij.build.BuildHttpAuthentication
import org.jetbrains.intellij.build.BuildHttpSession
import org.jetbrains.intellij.build.OwnedHttpResponseBody
import org.jetbrains.intellij.build.dependencies.BuildDependenciesDownloader.Credentials
import org.jetbrains.intellij.build.downloadAsText
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.net.URI
import java.net.http.HttpRequest
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.nio.file.StandardOpenOption.WRITE
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Flow
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@Timeout(20)
class BuildHttpSessionTest {
  @TempDir
  lateinit var tempDir: Path

  @Test
  fun `cancellation closes a response that the caller did not receive`() {
    val cancellations = AtomicInteger()
    OwnedHttpResponseBody().use { body ->
      body.subscriber.onSubscribe(countingSubscription(cancellations))
      val stream = body.subscriber.body.toCompletableFuture().get(5, TimeUnit.SECONDS)
      body.close()
      assertThat(cancellations.get()).isEqualTo(1)
      assertThrows<IOException> { stream.read() }
    }
  }

  @Test
  fun `a response that arrives after cancellation closes immediately`() {
    val cancellations = AtomicInteger()
    OwnedHttpResponseBody().use { body ->
      body.close()
      val stream = body.subscriber.body.toCompletableFuture().get(5, TimeUnit.SECONDS)
      body.subscriber.onSubscribe(countingSubscription(cancellations))
      assertThat(cancellations.get()).isEqualTo(1)
      assertThrows<IOException> { stream.read() }
    }
  }

  private fun countingSubscription(cancellations: AtomicInteger): Flow.Subscription {
    return object : Flow.Subscription {
      override fun request(count: Long) {}
      override fun cancel() {
        cancellations.incrementAndGet()
      }
    }
  }

  @Test
  fun `the session reuses connections and closes its virtual executor`() {
    val ports = CopyOnWriteArrayList<Int>()
    val session = BuildHttpSession()
    val client = session.client()
    val executor = client.executor().orElseThrow() as ExecutorService
    session.use {
      val virtual = CompletableFuture<Boolean>()
      executor.execute { virtual.complete(Thread.currentThread().isVirtual) }
      assertThat(virtual.get(5, TimeUnit.SECONDS)).isTrue()
      DownloadHttpServer { exchange ->
        ports.add(exchange.remoteAddress.port)
        exchange.respond("response")
      }.use { server ->
        repeat(3) { assertThat(downloadAsText(server.uri.toString(), session)).isEqualTo("response") }
        assertThat(ports).hasSize(3)
        assertThat(ports.toSet()).hasSize(1)
      }
    }
    assertThat(client.isTerminated).isTrue()
    assertThat(executor.isTerminated).isTrue()
    session.close()
    assertThrows<IllegalStateException> { session.client() }
  }

  @Test
  fun `closing one session does not close another session`() {
    DownloadHttpServer { it.respond("response") }.use { server ->
      BuildHttpSession().use { first ->
        BuildHttpSession().use { second ->
          downloadAsText(server.uri.toString(), first)
          downloadAsText(server.uri.toString(), second)
          first.close()
          assertThat(downloadAsText(server.uri.toString(), second)).isEqualTo("response")
        }
      }
    }
  }

  @Test
  fun `a deadline covers the body without closing a shared session`() {
    val requests = AtomicInteger()
    val started = CountDownLatch(1)
    DownloadHttpServer { exchange ->
      requests.incrementAndGet()
      if (exchange.requestURI.path == "/stalled") {
        exchange.sendResponseHeaders(200, 100)
        exchange.responseBody.write(0)
        exchange.responseBody.flush()
        started.countDown()
        CountDownLatch(1).await()
      }
      else exchange.respond("response")
    }.use { server ->
      BuildHttpSession(requestTimeout = 1.seconds).use { session ->
        taskScope {
          val stalled = fork("stalled response") {
            assertThrows<TimeoutException> { downloadAsText(server.uri.resolve("/stalled").toString(), session) }
          }
          val sibling = fork("sibling response") {
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue()
            downloadAsText(server.uri.toString(), session)
          }
          join()
          stalled.get()
          assertThat(sibling.get()).isEqualTo("response")
        }
        assertThat(downloadAsText(server.uri.toString(), session)).isEqualTo("response")
        assertThat(requests.get()).isEqualTo(3)
      }
    }
  }

  @Test
  fun `a deadline cancels a multipart upload without replay`() {
    val file = tempDir.resolve("upload")
    Files.newByteChannel(file, CREATE_NEW, WRITE).use {
      it.position(64L * 1024 * 1024 - 1)
      it.write(ByteBuffer.wrap(byteArrayOf(0)))
    }
    val requests = AtomicInteger()
    DownloadHttpServer { exchange ->
      requests.incrementAndGet()
      if (exchange.requestMethod == "POST") CountDownLatch(1).await()
      else exchange.respond("response")
    }.use { server ->
      BuildHttpSession(requestTimeout = 1.seconds).use { session ->
        val body = HttpRequest.BodyPublishers.concat(
          HttpRequest.BodyPublishers.ofString("--boundary\r\n"),
          HttpRequest.BodyPublishers.ofFile(file),
          HttpRequest.BodyPublishers.ofString("\r\n--boundary--\r\n"),
        )
        assertThrows<TimeoutException> { session.send(HttpRequest.newBuilder(server.uri).POST(body).build()) }
        assertThat(requests.get()).isEqualTo(1)
        assertThat(downloadAsText(server.uri.toString(), session)).isEqualTo("response")
      }
    }
    Files.delete(file)
  }

  @Test
  fun `a redirect cannot request credentials for another origin`() {
    val credentials = AtomicInteger()
    DownloadHttpServer { exchange ->
      assertThat(exchange.requestHeaders.getFirst("Authorization")).isNull()
      exchange.responseHeaders.set("WWW-Authenticate", "Basic realm=\"other\"")
      exchange.respond("challenge", 401)
    }.use { destination ->
      DownloadHttpServer { exchange ->
        exchange.responseHeaders.set("Location", destination.uri.toString())
        exchange.respond("", 302)
      }.use { source ->
        BuildHttpSession().use { session ->
          val response = session.send(HttpRequest.newBuilder(source.uri).GET().build(), BuildHttpAuthentication.Basic {
            credentials.incrementAndGet()
            Credentials("username", "password")
          })
          assertThat(response.statusCode).isEqualTo(401)
          assertThat(credentials.get()).isZero()
        }
      }
    }
  }

  @Test
  fun `an error body is bounded before it is materialized`() {
    DownloadHttpServer { exchange ->
      exchange.sendResponseHeaders(404, 100_000)
      exchange.responseBody.write(ByteArray(2048) { 'x'.code.toByte() })
      exchange.responseBody.flush()
      CountDownLatch(1).await()
    }.use { server ->
      BuildHttpSession(requestTimeout = 2.seconds).use { session ->
        val response = session.send(HttpRequest.newBuilder(server.uri).GET().build())
        assertThat(response.statusCode).isEqualTo(404)
        assertThat(response.body).hasSize(1024)
      }
    }
  }

  @Test
  fun `corrupt compressed responses fail without leaking the session`() {
    DownloadHttpServer { exchange ->
      if (exchange.requestURI.path == "/corrupt") {
        exchange.responseHeaders.set("Content-Encoding", "gzip")
        exchange.respond("not gzip")
      }
      else exchange.respond("response")
    }.use { server ->
      BuildHttpSession(initialRetryDelay = Duration.ZERO).use { session ->
        assertThrows<IOException> { session.send(HttpRequest.newBuilder(server.uri.resolve("/corrupt")).GET().build()) }
        assertThat(downloadAsText(server.uri.toString(), session)).isEqualTo("response")
      }
    }
  }

  @Test
  fun `a POST cannot opt into retries`() {
    BuildHttpSession().use { session ->
      val request = HttpRequest.newBuilder(URI("http://127.0.0.1:1")).POST(HttpRequest.BodyPublishers.noBody()).build()
      assertThrows<IllegalArgumentException> { session.send(request, attempts = 2) }
    }
  }
}
