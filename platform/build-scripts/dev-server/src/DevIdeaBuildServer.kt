// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package org.jetbrains.intellij.build.devServer

import com.intellij.openapi.util.io.NioFiles
import com.sun.net.httpserver.HttpContext
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import kotlin.system.exitProcess

private enum class DevIdeaBuildServerStatus {
  OK,
  FAILED,
  IN_PROGRESS,
  UNDEFINED
}

object DevIdeaBuildServer {
  private const val SERVER_PORT = 20854
  private val buildQueueLock = Semaphore(1, true)
  private val doneSignal = CountDownLatch(1)

  // <product / DevIdeaBuildServerStatus>
  private var productBuildStatus = HashMap<String, DevIdeaBuildServerStatus>()

  @JvmStatic
  fun main(args: Array<String>) {
    initLog()

    try {
      start()
    }
    catch (e: ConfigurationException) {
      e.printStackTrace()
      exitProcess(1)
    }
  }

  private fun start() {
    val additionalModules = getAdditionalModules()?.toList()
    val homePath = getHomePath()
    val productionClassOutput = (System.getenv("CLASSES_DIR")?.let { Path.of(it).toAbsolutePath().normalize() }
                                 ?: homePath.resolve("out/classes/production"))

    val httpServer = createHttpServer(
      buildServer = BuildServer(
        homePath = homePath,
        productionClassOutput = productionClassOutput
      ),
      requestTemplate = BuildRequest(
        platformPrefix = "",
        additionalModules = additionalModules ?: emptyList(),
        homePath = homePath,
        productionClassOutput = productionClassOutput,
      )
    )
    println("Listening on ${httpServer.address.hostString}:${httpServer.address.port}")
    println(
      "Custom plugins: ${additionalModules?.joinToString() ?: "not set (use VM property `additional.modules` to specify additional module ids)"}")
    println(
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

    println("Server stopping...")
    httpServer.stop(10)
    exitProcess(0)
  }

  private fun HttpExchange.getPlatformPrefix() = parseQuery(this.requestURI).get("platformPrefix")?.first() ?: "idea"

  private fun createBuildEndpoint(httpServer: HttpServer,
                                  buildServer: BuildServer,
                                  requestTemplate: BuildRequest): HttpContext? {
    return httpServer.createContext("/build") { exchange ->
      val platformPrefix = exchange.getPlatformPrefix()

      var statusMessage: String
      var statusCode = HttpURLConnection.HTTP_OK
      productBuildStatus.put(platformPrefix, DevIdeaBuildServerStatus.UNDEFINED)

      try {
        productBuildStatus.put(platformPrefix, DevIdeaBuildServerStatus.IN_PROGRESS)
        buildQueueLock.acquire()

        exchange.responseHeaders.add("Content-Type", "text/plain")
        runBlocking(Dispatchers.Default) {
          val ideBuilder = buildServer.checkOrCreateIdeBuilder(requestTemplate.copy(platformPrefix = platformPrefix))
          statusMessage = ideBuilder.pluginBuilder.buildChanged()
        }
        println(statusMessage)
      }
      catch (e: ConfigurationException) {
        statusCode = HttpURLConnection.HTTP_BAD_REQUEST
        productBuildStatus.put(platformPrefix, DevIdeaBuildServerStatus.FAILED)
        statusMessage = e.message!!
      }
      catch (e: Throwable) {
        productBuildStatus.put(platformPrefix, DevIdeaBuildServerStatus.FAILED)
        exchange.sendResponseHeaders(HttpURLConnection.HTTP_UNAVAILABLE, -1)
        System.err.println("Cannot handle build request: ")
        e.printStackTrace()
        return@createContext
      }
      finally {
        buildQueueLock.release()
      }

      productBuildStatus.put(platformPrefix, if (statusCode == HttpURLConnection.HTTP_OK) {
        DevIdeaBuildServerStatus.OK
      }
      else {
        DevIdeaBuildServerStatus.FAILED
      })

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

  private fun createHttpServer(buildServer: BuildServer, requestTemplate: BuildRequest): HttpServer {
    val httpServer = HttpServer.create()
    httpServer.bind(InetSocketAddress(InetAddress.getLoopbackAddress(), SERVER_PORT), 2)

    createBuildEndpoint(httpServer, buildServer, requestTemplate)
    createStatusEndpoint(httpServer)
    createStopEndpoint(httpServer)

    // Serve requests in parallel. Though, there is no guarantee, that 2 requests will be served for different endpoints
    httpServer.executor = Executors.newFixedThreadPool(2)
    return httpServer
  }
}

private fun parseQuery(url: URI): Map<String, List<String?>> {
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

internal fun doClearDirContent(child: Path) {
  Files.newDirectoryStream(child).use { stream ->
    for (child in stream) {
      NioFiles.deleteRecursively(child)
    }
  }
}

internal fun clearDirContent(dir: Path): Boolean {
  if (!Files.isDirectory(dir)) {
    return false
  }

  doClearDirContent(dir)
  return true
}