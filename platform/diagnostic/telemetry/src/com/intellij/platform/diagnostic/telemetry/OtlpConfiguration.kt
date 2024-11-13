// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.telemetry

import org.jetbrains.annotations.ApiStatus
import java.net.URI

@ApiStatus.Internal
object OtlpConfiguration {

  @JvmStatic
  fun getTraceEndpoint(): String? {
    return normalizeTraceEndpoint(System.getenv("OTLP_ENDPOINT") ?: System.getProperty("idea.diagnostic.opentelemetry.otlp"))
  }

  @JvmStatic
  fun getTraceEndpointURI(): URI? {
    return getTraceEndpoint()?.let {
      try {
        URI.create(it)
      }
      catch (_: Exception) {
        null
      }
    }
  }

  private fun normalizeTraceEndpoint(value: String?): String? {
    var endpoint = value?.takeIf(String::isNotEmpty) ?: return null
    endpoint = if (endpoint == "true") "http://127.0.0.1:4318" else endpoint.removeSuffix("/")
    return "$endpoint/v1/traces"
  }
}