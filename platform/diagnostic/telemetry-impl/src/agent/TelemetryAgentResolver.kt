// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.telemetry.impl.agent

import com.google.common.hash.Hashing
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.logger
import org.jetbrains.annotations.ApiStatus
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.inputStream

@ApiStatus.Internal
object TelemetryAgentResolver {

  private val LOGGER = logger<TelemetryAgentResolver>()

  private const val JAVA_AGENT_VERSION = "2.8.0"
  private const val JAVA_AGENT_FILE_SHA_256 = "d6ff4ea6fe5dc39c30123106b782940e261fbed21047a9761ab6a59b591092a0"
  private const val JAVA_AGENT_URI = "https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases" +
                                     "/download/v$JAVA_AGENT_VERSION/opentelemetry-javaagent.jar"

  fun getAgentLocation(): Path? {
    val location = PathManager.getSystemDir()
      .resolve("otlp-agent")
      .resolve("opentelemetry-javaagent-$JAVA_AGENT_VERSION.jar")
    if (location.exists()) {
      if (!isAgentHashMatch(location)) {
        LOGGER.warn("OpenTelemetry java agent hash did not match with the expected value")
      }
      return location
    }
    location.createParentDirectories()
    if (downloadAgent(location) && isAgentHashMatch(location)) {
      return location
    }
    LOGGER.warn("OpenTelemetry java agent hash did not match with the expected value. The agent is considered as invalid.")
    location.deleteIfExists()
    return null
  }

  private fun downloadAgent(location: Path): Boolean {
    LOGGER.info("Downloading OpenTelemetry java agent")
    val request = HttpRequest.newBuilder()
      .uri(URI.create(JAVA_AGENT_URI))
      .GET()
      .build()
    val response = HttpClient.newBuilder()
      .followRedirects(HttpClient.Redirect.ALWAYS)
      .build()
      .send(request, HttpResponse.BodyHandlers.ofFile(location))
    val success = response.statusCode() == 200
    if (!success) {
      LOGGER.warn("Unable to download an OpenTelemetry agent. Http status code: ${response.statusCode()}")
    }
    return success
  }

  private fun isAgentHashMatch(path: Path): Boolean {
    val actualHash = getFileHash(path)
    return JAVA_AGENT_FILE_SHA_256 == actualHash
  }

  @OptIn(ExperimentalStdlibApi::class)
  private fun getFileHash(path: Path): String {
    val hasher = Hashing.sha256().newHasher()
    path.inputStream().use { stream ->
      stream.buffered(1024).use { bufferedStream ->
        bufferedStream.iterator().forEach { byte -> hasher.putByte(byte) }
      }
    }
    return hasher.hash()
      .asBytes()
      .toHexString(HexFormat.Default)
  }
}