// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.telemetry.exporters

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import org.jetbrains.annotations.ApiStatus
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

@ApiStatus.Internal
object OpenTelemetryRawTraceExporter {

  private val LOG: Logger = logger<OpenTelemetryRawTraceExporter>()

  fun sendProtobuf(targetUri: URI, binaryTraces: ByteArray) {
    send(targetUri) {
      POST(HttpRequest.BodyPublishers.ofByteArray(binaryTraces))
      header("Content-Type", "application/x-protobuf")
    }
  }

  fun sendJson(targetUri: URI, json: ByteArray) {
    send(targetUri) {
      POST(HttpRequest.BodyPublishers.ofByteArray(json))
      header("Content-Type", "application/json")
    }
  }

  private fun send(targetUri: URI, customizer: HttpRequest.Builder.() -> HttpRequest.Builder) {
    try {
      val builder = HttpRequest.newBuilder().uri(targetUri)
      HttpClient.newHttpClient()
        .send(
          customizer(builder).build(),
          HttpResponse.BodyHandlers.discarding()
        )
    }
    catch (e: Exception) {
      LOG.warn("Unable to upload performance traces to the OTLP server ($targetUri)")
    }
  }
}