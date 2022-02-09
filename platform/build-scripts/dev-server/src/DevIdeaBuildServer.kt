// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.devServer

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.io.FileUtil
import com.sun.net.httpserver.HttpContext
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.PrintWriter
import java.io.StringWriter
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.logging.ConsoleHandler
import java.util.logging.Formatter
import java.util.logging.Level
import java.util.logging.LogRecord
import kotlin.io.path.createDirectories
import kotlin.system.exitProcess

@Suppress("GrazieInspection")
val skippedPluginModules = hashSetOf(
  "intellij.cwm.plugin", // quiche downloading should be implemented as a maven lib
)

val LOG: Logger = LoggerFactory.getLogger(DevIdeaBuildServer::class.java)

enum class DevIdeaBuildServerStatus(private val status: String) {
  OK("OK"),
  FAILED("FAILED"),
  IN_PROGRESS("IN_PROGRESS"),
  UNDEFINED("UNDEFINED");

  companion object {
    fun fromString(source: String): DevIdeaBuildServerStatus {
      return values().single { it.status == source.trim() }
    }
  }
}

class DevIdeaBuildServer {
  companion object {
    const val SERVER_PORT = 20854
    private val buildQueueLock = Semaphore(1, true)
    private val doneSignal = CountDownLatch(1)

    // <product / DevIdeaBuildServerStatus>
    private var productBuildStatus = mutableMapOf<String, DevIdeaBuildServerStatus>()

    @JvmStatic
    fun main(args: Array<String>) {
      initLog()

      try {
        start()
      }
      catch (e: ConfigurationException) {
        LOG.error(e.message)
        exitProcess(1)
      }
    }

    private fun initLog() {
      val root = java.util.logging.Logger.getLogger("")
      root.level = Level.INFO
      val handlers = root.handlers
      for (handler in handlers) {
        root.removeHandler(handler)
      }
      root.addHandler(ConsoleHandler().apply {
        formatter = object : Formatter() {
          override fun format(record: LogRecord): String {
            val timestamp = String.format("%1\$tT,%1\$tL", record.millis)
            return "$timestamp ${record.message}\n" + (record.thrown?.let { thrown ->
              StringWriter().also {
                thrown.printStackTrace(PrintWriter(it))
              }.toString()
            } ?: "")
          }
        }
      })
    }

    private fun start() {
      val buildServer = BuildServer(homePath = getHomePath())

      val httpServer = createHttpServer(buildServer)
      LOG.info("Listening on ${httpServer.address.hostString}:${httpServer.address.port}")
      @Suppress("SpellCheckingInspection")
      LOG.info(
        "Custom plugins: ${getAdditionalModules()?.joinToString() ?: "not set (use VM property `additional.modules` to specify additional module ids)"}")
      @Suppress("SpellCheckingInspection")
      LOG.info(
        "Run IDE on module intellij.platform.bootstrap with VM properties -Didea.use.dev.build.server=true -Djava.system.class.loader=com.intellij.util.lang.PathClassLoader")
      httpServer.start()

      // wait for ctrl-c
      Runtime.getRuntime().addShutdownHook(Thread {
        doneSignal.countDown()
      })

      try {
        doneSignal.await()
      }
      catch (ignore: InterruptedException) {
      }

      LOG.info("Server stopping...")
      httpServer.stop(10)
      exitProcess(0)
    }

    private fun HttpExchange.getPlatformPrefix() = parseQuery(this.requestURI).get("platformPrefix")?.first() ?: "idea"

    private fun createBuildEndpoint(httpServer: HttpServer, buildServer: BuildServer): HttpContext? {
      return httpServer.createContext("/build") { exchange ->
        val platformPrefix = exchange.getPlatformPrefix()

        var statusMessage: String
        var statusCode = HttpURLConnection.HTTP_OK
        productBuildStatus[platformPrefix] = DevIdeaBuildServerStatus.UNDEFINED

        try {
          productBuildStatus[platformPrefix] = DevIdeaBuildServerStatus.IN_PROGRESS
          buildQueueLock.acquire()

          exchange.responseHeaders.add("Content-Type", "text/plain")
          val ideBuilder = buildServer.checkOrCreateIdeBuilder(platformPrefix)
          statusMessage = ideBuilder.pluginBuilder.buildChanged()
          LOG.info(statusMessage)
        }
        catch (e: ConfigurationException) {
          statusCode = HttpURLConnection.HTTP_BAD_REQUEST
          productBuildStatus[platformPrefix] = DevIdeaBuildServerStatus.FAILED
          statusMessage = e.message!!
        }
        catch (e: Throwable) {
          productBuildStatus[platformPrefix] = DevIdeaBuildServerStatus.FAILED
          exchange.sendResponseHeaders(HttpURLConnection.HTTP_UNAVAILABLE, -1)
          LOG.error("Cannot handle build request", e)
          return@createContext
        }
        finally {
          buildQueueLock.release()
        }

        productBuildStatus[platformPrefix] =
          if (statusCode == HttpURLConnection.HTTP_OK) DevIdeaBuildServerStatus.OK
          else DevIdeaBuildServerStatus.FAILED

        val response = statusMessage.encodeToByteArray()
        exchange.sendResponseHeaders(statusCode, response.size.toLong())
        exchange.responseBody.apply {
          this.write(response)
          this.flush()
          this.close()
        }
      }
    }

    private fun createStatusEndpoint(httpServer: HttpServer): HttpContext? {
      return httpServer.createContext("/status") { exchange ->
        val platformPrefix = exchange.getPlatformPrefix()
        val buildStatus = productBuildStatus.getOrDefault(platformPrefix, DevIdeaBuildServerStatus.UNDEFINED)

        exchange.responseHeaders.add("Content-Type", "text/plain")
        val response = buildStatus.toString().encodeToByteArray()
        exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, response.size.toLong())
        exchange.responseBody.apply {
          this.write(response)
          this.flush()
          this.close()
        }
      }
    }

    private fun createStopEndpoint(httpServer: HttpServer): HttpContext? {
      return httpServer.createContext("/stop") { exchange ->

        exchange.responseHeaders.add("Content-Type", "text/plain")
        val response = "".encodeToByteArray()
        exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, response.size.toLong())
        exchange.responseBody.apply {
          this.write(response)
          this.flush()
          this.close()
        }

        doneSignal.countDown()
      }
    }

    private fun createHttpServer(buildServer: BuildServer): HttpServer {
      val httpServer = HttpServer.create()
      httpServer.bind(InetSocketAddress(InetAddress.getLoopbackAddress(), SERVER_PORT), 2)

      createBuildEndpoint(httpServer, buildServer)
      createStatusEndpoint(httpServer)
      createStopEndpoint(httpServer)

      // Serve requests in parallel. Though, there is no guarantee, that 2 requests will be served for different endpoints
      httpServer.executor = Executors.newFixedThreadPool(2)
      return httpServer
    }

    private fun getHomePath(): Path {
      val homePath: Path? = (PathManager.getHomePath(false) ?: PathManager.getHomePathFor(DevIdeaBuildServer::class.java))?.let {
        Paths.get(it)
      }
      if (homePath == null) {
        throw ConfigurationException("Could not find installation home path. Please specify explicitly via `idea.path` system property")
      }
      return homePath
    }
  }
}

fun parseQuery(url: URI): Map<String, List<String?>> {
  val query = url.query ?: return emptyMap()
  return query.splitToSequence("&")
    .map {
      val index = it.indexOf('=')
      val key = if (index > 0) it.substring(0, index) else it
      val value = if (index > 0 && it.length > index + 1) it.substring(index + 1) else null
      java.util.Map.entry(key, value)
    }
    .groupBy(keySelector = { it.key }, valueTransform = { it.value })
}

fun clearDirContent(dir: Path) {
  if (Files.isDirectory(dir)) {
    // because of problem on Windows https://stackoverflow.com/a/55198379/2467248
    FileUtil.delete(dir)
    dir.createDirectories()
  }
}

internal class ConfigurationException(message: String) : RuntimeException(message)