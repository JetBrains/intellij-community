// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.devServer

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.io.FileUtil
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import io.methvin.watcher.DirectoryChangeListener
import io.methvin.watcher.DirectoryWatcher
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.apache.log4j.ConsoleAppender
import org.apache.log4j.Level
import org.apache.log4j.PatternLayout
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI
import java.nio.file.ClosedWatchServiceException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess

private const val SERVER_PORT = 20854

val skippedPluginModules = hashSetOf(
  // skip intellij.cwm.plugin - quiche downloading should be implemented as a maven lib
  "intellij.cwm.plugin",
  // this plugin wants Kotlin plugin - not installed in IDEA running from sources
  "intellij.android.plugin"
)

val LOG: Logger = LoggerFactory.getLogger(DevIdeaBuildServer::class.java)

internal class DevIdeaBuildServer {
  companion object {
    @JvmStatic
    fun main(args: Array<String>) {
      // avoiding "log4j:WARN No appenders could be found"
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
  }
}

private fun start() {
  val buildServer = BuildServer(homePath = getHomePath())

  val httpServer = createHttpServer(buildServer)
  LOG.info("Listening on ${httpServer.address.hostString}:${httpServer.address.port}")
  @Suppress("SpellCheckingInspection")
  LOG.info("Custom plugins: ${getAdditionalModules()?.joinToString() ?: "not set (use VM property `additional.modules` to specify additional module ids)"}")
  @Suppress("SpellCheckingInspection")
  LOG.info("Run IDE on module intellij.platform.bootstrap with VM properties -Didea.use.dev.build.server=true -Djava.system.class.loader=com.intellij.util.lang.PathClassLoader")
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

@Serializable
data class Configuration(val products: Map<String, ProductConfiguration>)

@Serializable
data class ProductConfiguration(val modules: List<String>, @SerialName("class") val className: String)

class BuildServer(val homePath: Path) {
  private val outDir: Path = homePath.resolve("out/classes/production").toRealPath()
  private val configuration: Configuration

  private val platformPrefixToPluginBuilder = HashMap<String, IdeBuilder>()

  private val isDirWatcherStarted = AtomicBoolean()

  init {
    val jsonFormat = Json { isLenient = true }
    configuration = jsonFormat.decodeFromString(Configuration.serializer(), Files.readString(homePath.resolve("build/dev-build-server.json")))
  }

  @Synchronized
  fun getIdeBuilder(platformPrefix: String): IdeBuilder {
    var ideBuilder = platformPrefixToPluginBuilder.get(platformPrefix)
    if (ideBuilder != null) {
      return ideBuilder
    }

    val productConfiguration = configuration.products.get(platformPrefix)
                               ?: throw ConfigurationException("No production configuration for platform prefix `$platformPrefix`, " +
                                                               "please add to `dev-build-server.json` if needed")

    ideBuilder = initialBuild(productConfiguration, homePath, outDir)
    platformPrefixToPluginBuilder.put(platformPrefix, ideBuilder)

    if (isDirWatcherStarted.compareAndSet(false, true)) {
      watchChanges()
    }
    return ideBuilder
  }

  private fun watchChanges() {
    val moduleNamePathOffset = outDir.toString().length + 1
    val watcher = DirectoryWatcher.builder()
      .paths(listOf(outDir))
      .fileHashing(false)
      .listener(DirectoryChangeListener { event ->
        val path = event.path()
        // getName API is convenient, but it involves offsets computation and new Path, String, byte[] allocations,
        // so, use old good string
        val p = path.toString()
        if (p.endsWith("${File.separatorChar}classpath.index") || p.endsWith("${File.separatorChar}.DS_Store")) {
          return@DirectoryChangeListener
        }

        val moduleName = p.substring(moduleNamePathOffset, p.indexOf(File.separatorChar, moduleNamePathOffset))
        if (moduleName != "intellij.platform.ide.impl" && moduleName != "intellij.platform.ide") {
          for (ideBuilder in platformPrefixToPluginBuilder.values) {
            ideBuilder.moduleChanged(moduleName, path)
          }
        }
      })
      .build()
    Thread {
      try {
        watcher.watch()
      }
      catch (ignored: ClosedWatchServiceException) {
      }
    }.start()
    Runtime.getRuntime().addShutdownHook(Thread {
      watcher.close()
    })
  }
}

private fun createHttpServer(buildServer: BuildServer): HttpServer {
  val httpServer = HttpServer.create()
  httpServer.bind(InetSocketAddress(InetAddress.getLoopbackAddress(), SERVER_PORT), 4)
  httpServer.createContext("/build", HttpHandler { exchange ->
    val platformPrefix = parseQuery(exchange.requestURI).get("platformPrefix")?.first() ?: "idea"
    var statusMessage: String
    var statusCode = HttpURLConnection.HTTP_OK
    try {
      exchange.responseHeaders.add("Content-Type", "text/plain")
      statusMessage = buildServer.getIdeBuilder(platformPrefix).pluginBuilder.buildChanged()
      LOG.info(statusMessage)
    }
    catch (e: ConfigurationException) {
      statusCode = HttpURLConnection.HTTP_BAD_REQUEST
      statusMessage = e.message!!
    }
    catch (e: Throwable) {
      exchange.sendResponseHeaders(HttpURLConnection.HTTP_UNAVAILABLE, -1)
      LOG.error("Cannot handle build request", e)
      return@HttpHandler
    }

    val response = statusMessage.encodeToByteArray()
    exchange.sendResponseHeaders(statusCode, response.size.toLong())
    exchange.responseBody.write(response)
  })
  return httpServer
}

fun parseQuery(url: URI): Map<String, List<String?>> {
  val query = url.query ?: return emptyMap()
  return query.splitToSequence("&")
    .map {
      val index = it.indexOf('=')
      val key = if (index > 0) it.substring(0, index) else it
      val value = if (index > 0 && it.length > index + 1) it.substring(index + 1) else null
      AbstractMap.SimpleImmutableEntry(key, value)
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

private fun getHomePath(): Path {
  val homePath: Path? = (PathManager.getHomePath(false) ?: PathManager.getHomePathFor(DevIdeaBuildServer::class.java))?.let {
    Paths.get(it)
  }
  if (homePath == null) {
    throw ConfigurationException("Could not find installation home path. Please specify explicitly via `idea.path` system property")
  }
  return homePath
}

private class ConfigurationException(message: String) : RuntimeException(message)