// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.buildtool.cli.telemetryServer

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.debug.DebugProbes
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import kotlin.coroutines.jvm.internal.CoroutineDumpState

/**
 * Runs [block]. When the system property `fleet.build.haven.telemetry.server.enabled` is `true`, a loopback HTTP server
 * serves the telemetry handlers while [block] runs. The `/` page links to each handler.
 *
 * The server writes its URL to stderr. In a Bazel worker, stderr goes to the worker log.
 */
inline fun <T> withTelemetryServer(serviceName: String, block: () -> T): T {
  val server = when {
    System.getProperty("fleet.build.haven.telemetry.server.enabled") != "true" -> null
    else -> createTelemetryServer(serviceName).also { server ->
      server.start()
      System.err.println("$serviceName telemetry server: http://${server.address.hostString}:${server.address.port}/")
    }
  }
  try {
    return block()
  }
  finally {
    // the dispatcher thread of the server is not a daemon, so it keeps the JVM alive until the server stops
    server?.stop(0)
  }
}

/**
 * A plain-text page of the telemetry server at [path].
 */
private class TelemetryHandler(val path: String, val description: String, val dump: (PrintStream) -> Unit)

@PublishedApi
internal fun createTelemetryServer(serviceName: String): HttpServer {
  val handlers = listOf(
    coroutineDumpHandler(),
    threadDumpHandler(),
  )

  val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
  server.executor = Executors.newSingleThreadExecutor { runnable ->
    Thread(runnable, "$serviceName telemetry server").apply { isDaemon = true }
  }
  for (handler in handlers) {
    server.createContext(handler.path) { exchange ->
      exchange.respond(dumpToString(handler.dump))
    }
  }
  server.createContext("/") { exchange ->
    // the root context also receives each path that no other context matches
    if (exchange.requestURI.path != "/") {
      exchange.respond("Not found: ${exchange.requestURI.path}", status = 404)
      return@createContext
    }
    exchange.respond(indexPage(serviceName, handlers), contentType = "text/html")
  }
  return server
}

/**
 * The coroutine debug probes come from `intellij.platform.bootstrap.coroutine`, which must come before
 * kotlin-stdlib on the classpath. Thus no ByteBuddy agent is attached at runtime.
 */
@OptIn(ExperimentalCoroutinesApi::class)
private fun coroutineDumpHandler(): TelemetryHandler {
  // a stack trace capture on each coroutine creation is too expensive for a build
  DebugProbes.enableCreationStackTraces = false
  CoroutineDumpState.install()
  return TelemetryHandler("/debug/coroutines", "Kotlin Coroutines dump") { DebugProbes.dumpCoroutines(it) }
}

private fun threadDumpHandler(): TelemetryHandler {
  return TelemetryHandler("/debug/threads", "JVM threads dump") { out ->
    // not `ThreadInfo.toString()`, which truncates each stack trace to 8 frames
    for ((thread, stackTrace) in Thread.getAllStackTraces()) {
      out.println("\"${thread.name}\" #${thread.threadId()}${if (thread.isDaemon) " daemon" else ""} ${thread.state}")
      for (element in stackTrace) {
        out.println("\tat $element")
      }
      out.println()
    }
  }
}

private fun indexPage(serviceName: String, handlers: List<TelemetryHandler>): String {
  val title = "Telemetry Server [$serviceName] (pid ${ProcessHandle.current().pid()})"
  val rows = handlers.joinToString(separator = "\n") { """<tr><td><a href="${it.path}">${it.path}</a></td><td>${it.description}</td></tr>""" }
  return """
    |<!DOCTYPE html>
    |<html>
    |<head>
    |<meta charset="utf-8">
    |<title>$title</title>
    |<style>td { padding: 2px 16px 2px 0; }</style>
    |</head>
    |<body>
    |<h1>$title</h1>
    |<table>
    |$rows
    |</table>
    |</body>
    |</html>
    |""".trimMargin()
}

private fun dumpToString(dump: (PrintStream) -> Unit): String {
  val bytes = ByteArrayOutputStream()
  PrintStream(bytes, true, Charsets.UTF_8).use(dump)
  return bytes.toString(Charsets.UTF_8)
}

private fun HttpExchange.respond(body: String, contentType: String = "text/plain", status: Int = 200) {
  val bytes = body.toByteArray(Charsets.UTF_8)
  responseHeaders.add("Content-Type", "$contentType; charset=utf-8")
  sendResponseHeaders(status, bytes.size.toLong())
  responseBody.use { it.write(bytes) }
}
