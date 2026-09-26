// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.bazel.wasmjs.test.harness.runner

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executors

internal interface StaticServer {
  suspend fun serve(): URI
}

internal suspend fun <T> withStaticServer(
  contentRoot: Path,
  indexPage: String,
  log: InfrastructureLog,
  block: suspend CoroutineScope.(StaticServer) -> T,
): T = coroutineScope {
  StaticServerImpl(contentRoot, indexPage, log).use { server ->
    block(server)
  }
}

/**
 * Plain directory server over [contentRoot], bound to an ephemeral loopback port. The root page
 * (`/`, `index.html`) is served from the in-memory [indexPage] instead of the directory.
 *
 * This piece knows a directory, one page, and HTTP; nothing about tests or browsers. Misses are
 * recorded in [log]: a 404 is otherwise invisible on the page side, where a missing module
 * surfaces only as a generic dynamic-import TypeError.
 */
private class StaticServerImpl(
  contentRoot: Path,
  private val indexPage: String,
  private val log: InfrastructureLog,
) : AutoCloseable, StaticServer {
  private val contentRoot: Path = contentRoot.toAbsolutePath().normalize()
  private val server: HttpServer = HttpServer.create()

  /** Starts the server and returns its base URI. */
  override suspend fun serve(): URI = withContext(Dispatchers.IO) {
    server.createContext("/", ::handle)
    server.executor = Executors.newVirtualThreadPerTaskExecutor()
    server.bind(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
    server.start()
    URI("http://${server.address.address.hostAddress}:${server.address.port}/")
  }

  override fun close() {
    server.stop(0)
  }

  private fun handle(exchange: HttpExchange) {
    exchange.use {
      try {
        val relative = when (val path = exchange.requestURI.path) {
          "/" -> "index.html"
          else -> path.removePrefix("/")
        }
        when (relative) {
          "index.html" -> exchange.respondPage(indexPage)
          else -> {
            // Lexical normalization (not toRealPath) keeps `..` escapes out while still allowing the
            // content tree to be built out of symlinks, which is how the Bazel rule assembles it.
            val resolved = contentRoot.resolve(relative).normalize()
            when {
              !resolved.startsWith(contentRoot) -> exchange.respondNotFound(log)
              !Files.isRegularFile(resolved) -> exchange.respondNotFound(log)
              else -> exchange.respondFile(resolved)
            }
          }
        }
      }
      catch (e: Exception) {
        // A failed response is invisible on the page side (a generic network/import error
        // there); without this record the JDK server would swallow the exception entirely.
        log.appendLine("static server error for ${exchange.requestURI.path}: $e")
        try {
          exchange.sendResponseHeaders(500, -1)
        }
        catch (_: IOException) {
          // The response headers were already sent: closing the truncated exchange is all
          // that is left to do.
        }
      }
    }
  }
}

private fun HttpExchange.respondNotFound(log: InfrastructureLog) {
  log.appendLine("static server 404: ${requestURI.path}")
  sendResponseHeaders(404, -1)
}

private fun HttpExchange.respondPage(page: String) {
  val bytes = page.toByteArray()
  responseHeaders.set("Content-Type", "text/html; charset=utf-8")
  sendResponseHeaders(200, bytes.size.toLong())
  responseBody.use { body -> body.write(bytes) }
}

private fun HttpExchange.respondFile(file: Path) {
  // Opened before the headers go out: an unreadable file becomes a clean 500 (through the
  // handler's catch) instead of a 200 with a truncated body the browser waits on.
  Files.newInputStream(file).use { content ->
    responseHeaders.set("Content-Type", contentTypeOf(file))
    sendResponseHeaders(200, Files.size(file))
    responseBody.use { body -> content.transferTo(body) }
  }
}

private fun contentTypeOf(file: Path): String = when (file.fileName.toString().substringAfterLast('.', missingDelimiterValue = "")) {
  "mjs", "js" -> "text/javascript"
  "wasm" -> "application/wasm"
  "html" -> "text/html; charset=utf-8"
  "css" -> "text/css"
  "json", "map" -> "application/json"
  "txt" -> "text/plain; charset=utf-8"
  "svg" -> "image/svg+xml"
  "png" -> "image/png"
  "jpg", "jpeg" -> "image/jpeg"
  "webp" -> "image/webp"
  "woff" -> "font/woff"
  "woff2" -> "font/woff2"
  else -> "application/octet-stream"
}
