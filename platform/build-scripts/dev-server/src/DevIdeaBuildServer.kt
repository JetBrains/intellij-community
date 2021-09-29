// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.devServer

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.io.FileUtil
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.apache.log4j.ConsoleAppender
import org.apache.log4j.Level
import org.apache.log4j.PatternLayout
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.CountDownLatch
import kotlin.system.exitProcess

val skippedPluginModules = hashSetOf(
  "intellij.cwm.plugin", // quiche downloading should be implemented as a maven lib
)

val LOG: Logger = LoggerFactory.getLogger(DevIdeaBuildServer::class.java)

enum class DevIdeaBuildServerStatus(private val status: String) {
  OK("OK"),
  FAILED("FAILED"),
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

    // <product / DevIdeaBuildServerStatus>
    private var productBuildStatus = mutableMapOf<String, DevIdeaBuildServerStatus>()

    @JvmStatic
    fun main(args: Array<String>) {
      // avoiding "log4j:WARN No appenders could be found"
      System.setProperty("log4j.defaultInitOverride", "true")
      val root = org.apache.log4j.Logger.getRootLogger()
      root.level = Level.INFO
      root.addAppender(ConsoleAppender(PatternLayout("%d{ABSOLUTE} %m%n")))

      try {
        start()
      }
      catch (e: ConfigurationException) {
        LOG.error(e.message)
        exitProcess(1)
      }
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

      val doneSignal = CountDownLatch(1)

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
    }

    private fun HttpExchange.getPlatformPrefix() = parseQuery(this.requestURI).get("platformPrefix")?.first() ?: "idea"

    private fun createHttpServer(buildServer: BuildServer): HttpServer {
      val httpServer = HttpServer.create()
      httpServer.bind(InetSocketAddress(InetAddress.getLoopbackAddress(), SERVER_PORT), 4)

      httpServer.createContext("/build") { exchange ->
        val platformPrefix = exchange.getPlatformPrefix()

        var statusMessage: String
        var statusCode = HttpURLConnection.HTTP_OK
        try {
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

        productBuildStatus[platformPrefix] =
          if (statusCode == HttpURLConnection.HTTP_OK) DevIdeaBuildServerStatus.OK
          else DevIdeaBuildServerStatus.FAILED

        val response = statusMessage.encodeToByteArray()
        exchange.sendResponseHeaders(statusCode, response.size.toLong())
        exchange.responseBody.write(response)
      }

      httpServer.createContext("/status") { exchange ->
        val platformPrefix = exchange.getPlatformPrefix()
        val buildStatus = productBuildStatus.getOrDefault(platformPrefix, DevIdeaBuildServerStatus.UNDEFINED)

        exchange.responseHeaders.add("Content-Type", "text/plain")
        val response = buildStatus.toString().encodeToByteArray()
        exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, response.size.toLong())
        exchange.responseBody.write(response)
      }

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
    Files.newDirectoryStream(dir).use {
      for (path in it) {
        FileUtil.delete(dir)
      }
    }
  }
}

internal class ConfigurationException(message: String) : RuntimeException(message)