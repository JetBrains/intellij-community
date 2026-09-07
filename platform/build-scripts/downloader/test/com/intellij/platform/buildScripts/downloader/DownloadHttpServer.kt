package com.intellij.platform.buildScripts.downloader

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.IOException
import java.net.InetSocketAddress
import java.net.URI
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors

class DownloadHttpServer(handler: (HttpExchange) -> Unit) : AutoCloseable {
  private val executor = Executors.newVirtualThreadPerTaskExecutor()
  private val failures = ConcurrentLinkedQueue<Throwable>()
  private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
  val uri: URI = URI("http://127.0.0.1:${server.address.port}")

  init {
    server.executor = executor
    server.createContext("/") { exchange ->
      exchange.use {
        try {
          handler(it)
        }
        catch (_: InterruptedException) {
          Thread.currentThread().interrupt()
        }
        catch (_: IOException) {
        }
        catch (failure: Throwable) {
          failures.add(failure)
        }
      }
    }
    server.start()
  }

  override fun close() {
    server.stop(0)
    executor.shutdownNow()
    executor.close()
    failures.poll()?.let { throw AssertionError("The HTTP handler failed", it) }
  }
}

fun HttpExchange.respond(body: ByteArray, status: Int = 200) {
  sendResponseHeaders(status, if (body.isEmpty()) -1 else body.size.toLong())
  responseBody.use { it.write(body) }
}

fun HttpExchange.respond(body: String, status: Int = 200) {
  respond(body.toByteArray(), status)
}
