// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.telemetry.exporters

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

object OpenTelemetryRawTraceExporter {

  private val LOG: Logger = logger<OpenTelemetryRawTraceExporter>()

  enum class Protocol(val contentType: String) {
    PROTOBUF("application/x-protobuf"),
    JSON("application/json")
  }

  fun export(targetUri: URI, binaryTraces: ByteArray, protocol: Protocol) {
    try {
      HttpClient.newHttpClient()
        .send(HttpRequest.newBuilder()
                .POST(HttpRequest.BodyPublishers.ofByteArray(binaryTraces))
                .uri(targetUri)
                .header("Content-Type", protocol.contentType)
                .build(),
              HttpResponse.BodyHandlers.discarding())
    }
    catch (e: Exception) {
      LOG.error("Unable to upload performance traces to the OTLP server")
    }
  }
}