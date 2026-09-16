// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.api

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.ByteArrayOutputStream
import java.net.http.HttpClient
import java.nio.charset.StandardCharsets
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPOutputStream
import com.intellij.collaboration.api.httpclient.HttpClientFactory

/**
 * Builds an [HttpApiHelper] backed by a client that never goes through a proxy (so requests to the local mock server
 * are deterministic regardless of the environment's proxy configuration) and negotiates the given [clientVersion].
 */
internal fun testHttpApiHelper(
  clientVersion: HttpClient.Version = HttpClient.Version.HTTP_1_1,
  redirectPolicy: HttpClient.Redirect = HttpClient.Redirect.NORMAL,
): HttpApiHelper =
  HttpApiHelper(clientFactory = object : HttpClientFactory {
    override fun createClient(): HttpClient =
      HttpClient.newBuilder()
        .version(clientVersion)
        .followRedirects(redirectPolicy)
        .proxy(HttpClient.Builder.NO_PROXY)
        .build()
  })

/** A snapshot of a request as it was received by the mock server. */
internal class RecordedRequest(
  val method: String,
  val path: String,
  private val headers: Map<String, List<String>>,
  val body: String,
) {
  fun header(name: String): String? =
    headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value?.firstOrNull()
}

/**
 * Registers a handler at [path] that records the incoming request into [recorder] (if given) and replies with the
 * given [status], [contentType], optional gzip [contentEncoding] and the payload produced by [body].
 *
 * An empty payload results in a response without a body (`Content-Length: 0`).
 */
internal fun HttpServer.respondWith(
  path: String = "/",
  status: Int = 200,
  contentType: String? = null,
  contentEncoding: String? = null,
  recorder: MutableList<RecordedRequest>? = null,
  headers: Map<String, String> = emptyMap(),
  body: () -> ByteArray = { ByteArray(0) },
) {
  createContext(path) { exchange: HttpExchange ->
    exchange.use { exchange ->
      val requestBytes = exchange.requestBody.use { it.readBytes() }
      recorder?.add(RecordedRequest(
        method = exchange.requestMethod,
        path = exchange.requestURI.path,
        headers = exchange.requestHeaders.mapValues { it.value.toList() },
        body = String(requestBytes, StandardCharsets.UTF_8),
      ))

      val payload = body()
      val encoded = if (contentEncoding.equals("gzip", ignoreCase = true)) gzip(payload) else payload
      contentType?.let { exchange.responseHeaders.add("Content-Type", it) }
      contentEncoding?.let { exchange.responseHeaders.add("Content-Encoding", it) }
      headers.forEach { (name, value) -> exchange.responseHeaders.add(name, value) }

      if (encoded.isEmpty()) {
        exchange.sendResponseHeaders(status, -1)
      }
      else {
        exchange.sendResponseHeaders(status, encoded.size.toLong())
        exchange.responseBody.write(encoded)
      }
    }
  }
}

/** Convenience overload for text responses. */
internal fun HttpServer.respondWithText(
  path: String = "/",
  status: Int = 200,
  contentType: String? = "text/plain; charset=UTF-8",
  contentEncoding: String? = null,
  recorder: MutableList<RecordedRequest>? = null,
  text: String,
) = respondWith(path, status, contentType, contentEncoding, recorder) { text.toByteArray(StandardCharsets.UTF_8) }

/** Convenience overload for JSON responses. */
internal fun HttpServer.respondWithJson(
  path: String = "/",
  status: Int = 200,
  contentEncoding: String? = null,
  recorder: MutableList<RecordedRequest>? = null,
  json: String,
) = respondWith(path, status, "application/json", contentEncoding, recorder) { json.toByteArray(StandardCharsets.UTF_8) }

/**
 * Registers a handler at [from] that records the incoming request into [recorder] (if given) and replies with a
 * redirect [status] pointing at [to] (which may be absolute or a server-relative path).
 */
internal fun HttpServer.redirect(
  from: String = "/",
  to: String,
  status: Int = 302,
  recorder: MutableList<RecordedRequest>? = null,
) = respondWith(from, status, recorder = recorder, headers = mapOf("Location" to to))

/**
 * Registers a handler at [path] that never sends a response, blocking until the connection is torn down.
 * Useful for provoking a client-side read timeout.
 *
 * [onRequest] is invoked (on the server thread) as soon as the request is received, before the handler starts blocking,
 * so a test can deterministically learn that the request is in flight.
 */
internal fun HttpServer.hang(path: String = "/", onRequest: () -> Unit = {}): AutoCloseable {
  val release = CountDownLatch(1)
  createContext(path) { exchange ->
    exchange.use {
      exchange.requestBody.use { it.readBytes() }
      onRequest()
      release.await(30, TimeUnit.SECONDS)
    }
  }
  return AutoCloseable { release.countDown() }
}

/**
 * Registers a handler at [path] that sends a `200` response with headers and the optional [head] bytes, then keeps
 * the response body open (without an end-of-stream) until closed or a hard 30s cap elapses.
 *
 * This lets a test start reading the body and then block, so the cancellation of a slow body read can be exercised.
 */
internal fun HttpServer.respondThenStall(path: String = "/", head: ByteArray = ByteArray(0)): AutoCloseable {
  val release = CountDownLatch(1)
  createContext(path) { exchange ->
    exchange.use {
      exchange.requestBody.use { it.readBytes() }
      exchange.responseHeaders.add("Content-Type", "text/plain; charset=UTF-8")
      exchange.sendResponseHeaders(200, 0) // 0 -> arbitrary length, terminated by closing the exchange
      if (head.isNotEmpty()) {
        exchange.responseBody.write(head)
        exchange.responseBody.flush()
      }
      release.await(30, TimeUnit.SECONDS)
    }
  }
  return AutoCloseable { release.countDown() }
}

internal fun newRecorder(): MutableList<RecordedRequest> = CopyOnWriteArrayList()

private fun gzip(bytes: ByteArray): ByteArray =
  ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(bytes) } }.toByteArray()
