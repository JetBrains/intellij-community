// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.api

import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.TestFixtures
import com.intellij.testFramework.junit5.http.localhostHttpServer
import com.intellij.testFramework.junit5.http.url
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.ConnectException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException
import java.nio.charset.Charset
import java.time.Duration
import java.util.concurrent.CancellationException
import kotlin.time.Duration.Companion.seconds

/**
 * Integration tests for [HttpApiHelper] running against a local [HttpServer].
 *
 * Runs against both an HTTP/1.1 and an HTTP/2 client (see the concrete subclasses at the bottom). Note that the mock
 * [HttpServer] only speaks HTTP/1.1, so the HTTP/2 client negotiates down via the cleartext `h2c` upgrade — this
 * verifies the helper behaves correctly regardless of the configured client version.
 */
@TestFixtures
abstract class HttpApiHelperTest(private val clientVersion: HttpClient.Version) {
  private val serverFixture: TestFixture<HttpServer> = localhostHttpServer()
  private val server: HttpServer get() = serverFixture.get()

  // a second server on a different port, used as a cross-origin redirect target
  private val otherServerFixture: TestFixture<HttpServer> = localhostHttpServer()
  private val otherServer: HttpServer get() = otherServerFixture.get()

  private val helper: HttpApiHelper = testHttpApiHelper(clientVersion)

  @Test
  fun `request pre-configures common headers and a timeout`() = timeoutRunBlocking {
    val request = helper.request(server.url).build()

    assertThat(request.headers().firstValue("Accept-Encoding")).hasValue("gzip")
    assertThat(request.headers().firstValue("User-Agent")).hasValue("JetBrains IDE")
    assertThat(request.timeout()).isPresent
    Unit
  }

  @Test
  fun `sendAndAwait returns the raw response`() = timeoutRunBlocking {
    server.respondWithText(text = "hello")

    val request = helper.request(server.url).build()
    val response = helper.sendAndAwait(request, HttpResponse.BodyHandlers.ofString())

    assertThat(response.statusCode()).isEqualTo(200)
    assertThat(response.body()).isEqualTo("hello")
    Unit
  }

  @Test
  fun `sendAndRead exposes the mime type and charset to the body reader`() = timeoutRunBlocking {
    server.respondWithText(text = "hello", contentType = "text/plain; charset=UTF-8")

    var seenMimeType: String? = null
    var seenCharset: Charset? = null
    val request = helper.request(server.url).build()
    val body = helper.sendAndReadBody(request) { mimeType, charset ->
      seenMimeType = mimeType
      seenCharset = charset
      reader(charset ?: Charsets.UTF_8).readText()
    }

    assertThat(body).isEqualTo("hello")
    assertThat(seenMimeType).isEqualTo("text/plain")
    assertThat(seenCharset).isEqualTo(Charsets.UTF_8)
    Unit
  }

  @Test
  fun `sendAndRead transparently decodes a gzip-encoded response`() = timeoutRunBlocking {
    server.respondWithText(text = "gzipped payload", contentEncoding = "gzip")

    val request = helper.request(server.url).build()
    val body = helper.sendAndReadBody(request) { _, charset -> reader(charset ?: Charsets.UTF_8).readText() }

    assertThat(body).isEqualTo("gzipped payload")
    Unit
  }

  @Test
  fun `sendAndRead forces gzip accept-encoding even when the request asks otherwise`() = timeoutRunBlocking {
    val recorder = newRecorder()
    server.respondWithText(text = "ok", recorder = recorder)

    val request = HttpRequest.newBuilder(URI.create(server.url)).header("Accept-Encoding", "identity").GET().build()
    helper.sendAndReadBody(request) { _, _ -> readAllBytes() }

    assertThat(recorder.single().header("Accept-Encoding")).isEqualTo("gzip")
    Unit
  }

  @Test
  fun `sendAndAwait without a body handler consumes a successful response`() = timeoutRunBlocking {
    val recorder = newRecorder()
    server.respondWithText(text = "ignored", recorder = recorder)

    val request = helper.request(server.url).build()
    helper.sendAndAwait(request)

    assertThat(recorder.size).isEqualTo(1)
    Unit
  }

  @Test
  fun `sendAndRead sends the request method and body`() = timeoutRunBlocking {
    val recorder = newRecorder()
    server.respondWithText(text = "ok", recorder = recorder)

    val request = helper.request(server.url).POST(HttpRequest.BodyPublishers.ofString("payload")).build()
    helper.sendAndReadBody(request) { _, _ -> readAllBytes() }

    val recorded = recorder.single()
    assertThat(recorded.method).isEqualTo("POST")
    assertThat(recorded.body).isEqualTo("payload")
    Unit
  }

  @Test
  fun `an error status code is reported as HttpStatusErrorException with the body`() = timeoutRunBlocking {
    server.respondWithText(status = 404, text = "not found here")

    val request = helper.request(server.url).build()
    val error = runCatching { helper.sendAndReadBody(request) { _, _ -> readAllBytes() } }.exceptionOrNull()

    assertThat(error).isInstanceOf(HttpStatusErrorException::class.java)
    error as HttpStatusErrorException
    assertThat(error.statusCode).isEqualTo(404)
    assertThat(error.body).contains("not found here")
    Unit
  }

  @Test
  fun `a 304 Not Modified is surfaced as a normal response rather than an error`() = timeoutRunBlocking {
    // a conditional request that hits the cache is not an error: only 4xx/5xx are, so the 304 flows through
    server.respondWith(status = 304)

    val request = helper.request(server.url).build()
    val response = helper.sendAndRead(request) { _, _ -> readAllBytes() }

    assertThat(response.statusCode()).isEqualTo(304)
    assertThat(response.body()).isEmpty()
    Unit
  }

  @Test
  fun `a non-following client surfaces a redirect as a normal response rather than an error`() = timeoutRunBlocking {
    // with a client that does not auto-follow, a 3xx is the caller's responsibility (e.g. manual redirect handling),
    // so it must be delivered as a normal response carrying Location, not raised as an HttpStatusErrorException
    val nonFollowingHelper = testHttpApiHelper(clientVersion, HttpClient.Redirect.NEVER)
    server.redirect(from = "/", to = "/elsewhere", status = 302)

    val request = nonFollowingHelper.request(server.url).build()
    val response = nonFollowingHelper.sendAndRead(request) { _, _ -> readAllBytes() }

    assertThat(response.statusCode()).isEqualTo(302)
    assertThat(response.headers().firstValue("Location")).hasValue("/elsewhere")
    Unit
  }

  @Test
  fun `an unresolvable host is reported as an IOException`() = timeoutRunBlocking {
    val request = helper.request("http://this-host-should-not-exist.invalid/").build()
    val error = runCatching { helper.sendAndAwait(request, HttpResponse.BodyHandlers.ofString()) }.exceptionOrNull()

    // the concrete subtype depends on the environment's DNS (UnknownHostException vs. a ConnectException to a
    // redirected address), but the helper always surfaces it as an IOException
    assertThat(error).isInstanceOf(IOException::class.java)
    Unit
  }

  @Test
  fun `a refused connection is reported as an IOException`() = timeoutRunBlocking {
    // port 1 is privileged and reliably not listening on loopback
    val request = helper.request("http://127.0.0.1:1/").build()
    val error = runCatching { helper.sendAndAwait(request, HttpResponse.BodyHandlers.ofString()) }.exceptionOrNull()

    assertThat(error)
      .isInstanceOf(IOException::class.java)
      .isInstanceOf(ConnectException::class.java)
    Unit
  }

  @Test
  fun `a read timeout is reported as an IOException`() = timeoutRunBlocking {
    server.hang().use {
      val request = helper.request(server.url).timeout(Duration.ofMillis(300)).build()
      val error = runCatching { helper.sendAndAwait(request, HttpResponse.BodyHandlers.ofString()) }.exceptionOrNull()

      assertThat(error)
        .isInstanceOf(IOException::class.java)
        .isInstanceOf(HttpTimeoutException::class.java)
    }
    Unit
  }

  @Test
  fun `sendAndRead follows a temporary redirect to the final response`() = timeoutRunBlocking {
    val recorder = newRecorder()
    server.redirect(from = "/", to = "/target", status = 302, recorder = recorder)
    server.respondWithText(path = "/target", text = "final", recorder = recorder)

    val request = helper.request(server.url).build()
    val body = helper.sendAndReadBody(request) { _, charset -> reader(charset ?: Charsets.UTF_8).readText() }

    assertThat(body).isEqualTo("final")
    assertThat(recorder.map { it.path }).containsExactly("/", "/target")
    Unit
  }

  @Test
  fun `sendAndRead follows a permanent redirect to the final response`() = timeoutRunBlocking {
    server.redirect(from = "/", to = "/moved", status = 301)
    server.respondWithText(path = "/moved", text = "relocated")

    val request = helper.request(server.url).build()
    val response = helper.sendAndAwait(request, HttpResponse.BodyHandlers.ofString())

    assertThat(response.statusCode()).isEqualTo(200)
    assertThat(response.body()).isEqualTo("relocated")
    // the final response reflects the redirected URI
    assertThat(response.uri().path).isEqualTo("/moved")
    Unit
  }

  @Test
  fun `sendAndRead drops the Authorization header on a cross-origin redirect`() = timeoutRunBlocking {
    val originRecorder = newRecorder()
    val targetRecorder = newRecorder()
    // redirect to a second server on a different port, i.e. a different origin
    server.redirect(from = "/", to = otherServer.url + "/target", status = 302, recorder = originRecorder)
    otherServer.respondWithText(path = "/target", text = "final", recorder = targetRecorder)

    val request = helper.request(server.url).header("Authorization", "Bearer secret").build()
    val body = helper.sendAndReadBody(request) { _, charset -> reader(charset ?: Charsets.UTF_8).readText() }

    assertThat(body).isEqualTo("final")
    // the credentials are sent to the original origin, but must not be forwarded across origins
    assertThat(originRecorder.single().header("Authorization")).isEqualTo("Bearer secret")
    assertThat(targetRecorder.single().header("Authorization")).isNull()
    Unit
  }

  @Test
  fun `cancellation aborts a request while awaiting the response`() = timeoutRunBlocking {
    // the server signals once it has the request, so the client is probably in flight awaiting the response
    val requestReceived = CompletableDeferred<Unit>()
    server.hang(onRequest = { requestReceived.complete(Unit) }).use {
      val request = helper.request(server.url).build()
      val job = async(Dispatchers.IO) {
        helper.sendAndAwait(request, HttpResponse.BodyHandlers.ofString())
      }

      requestReceived.await()
      job.cancel()

      val outcome = withTimeoutOrNull(5.seconds) { runCatching { job.await() }.exceptionOrNull() }
      assertThat(outcome)
        .describedAs("awaiting a slow response should be cancelled promptly")
        .isInstanceOf(CancellationException::class.java)
    }
    Unit
  }

  @Test
  fun `cancellation stops a slow response body processing`() = timeoutRunBlocking {
    server.respondThenStall(head = "partial".toByteArray(Charsets.UTF_8)).use {
      // the reader signals from inside the body handler, so cancellation provably happens while it is blocked reading
      val readingStarted = CompletableDeferred<Unit>()
      val request = helper.request(server.url).build()
      val job = async(Dispatchers.IO) {
        helper.sendAndReadBody(request) { _, charset ->
          readingStarted.complete(Unit)
          // blocks until end-of-stream, which the server withholds until the latch is released
          reader(charset ?: Charsets.UTF_8).readText()
        }
      }

      readingStarted.await()
      job.cancel()

      val outcome = withTimeoutOrNull(5.seconds) { runCatching { job.await() }.exceptionOrNull() }
      assertThat(outcome)
        .describedAs("a cancelled slow body read must not keep running until the server finishes responding")
        .isInstanceOf(CancellationException::class.java)
    }
    Unit
  }
}

class Http1ApiHelperTest : HttpApiHelperTest(HttpClient.Version.HTTP_1_1)

class Http2ApiHelperTest : HttpApiHelperTest(HttpClient.Version.HTTP_2)
