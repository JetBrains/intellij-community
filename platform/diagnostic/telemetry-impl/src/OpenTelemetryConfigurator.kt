// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.telemetry.impl

import com.intellij.openapi.util.SystemInfoRt
import com.intellij.platform.diagnostic.telemetry.OtlpConfiguration
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.common.AttributesBuilder
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.OpenTelemetrySdkBuilder
import io.opentelemetry.sdk.resources.Resource
import org.jetbrains.annotations.ApiStatus
import java.time.Instant
import java.time.format.DateTimeFormatter

@ApiStatus.Internal
class OpenTelemetryConfigurator(@JvmField internal val sdkBuilder: OpenTelemetrySdkBuilder,
                                serviceName: String = "",
                                serviceVersion: String = "",
                                serviceNamespace: String = "",
                                customResourceBuilder: ((AttributesBuilder) -> Unit)? = null) {
  val resource: Resource = Resource.create(
    Attributes.builder()
      .put(AttributeKey.stringKey("service.name"), serviceName)
      .put(AttributeKey.stringKey("service.version"), serviceVersion)
      .put(AttributeKey.stringKey("service.namespace"), serviceNamespace)
      .put(AttributeKey.stringKey("os.type"), SystemInfoRt.OS_NAME)
      .put(AttributeKey.stringKey("os.version"), SystemInfoRt.OS_VERSION)
      .put(AttributeKey.stringKey("host.arch"), System.getProperty("os.arch"))
      .put(AttributeKey.stringKey("service.instance.id"), DateTimeFormatter.ISO_INSTANT.format(Instant.now()))
      .also {
        customResourceBuilder?.invoke(it)
      }
      .build()
  )

  companion object {
    fun create(
      serviceName: String,
      serviceVersion: String,
      serviceNamespace: String,
    ): OpenTelemetryConfigurator {
      return OpenTelemetryConfigurator(
        sdkBuilder = OpenTelemetrySdk.builder(),
        serviceName = serviceName,
        serviceVersion = serviceVersion,
        serviceNamespace = serviceNamespace,
        customResourceBuilder = { attributes ->
          // don't write username to file - it maybe private information
          if (OtlpConfiguration.isTraceEnabled()) {
            attributes.put(AttributeKey.stringKey("process.owner"), System.getProperty("user.name") ?: "unknown")
          }
        },
      )
    }
  }
}