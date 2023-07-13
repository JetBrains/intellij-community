// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.telemetry.impl

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.platform.diagnostic.telemetry.AsyncSpanExporter
import com.intellij.platform.diagnostic.telemetry.OpenTelemetryDefaultConfigurator
import com.intellij.platform.diagnostic.telemetry.OpenTelemetryUtils.IDEA_DIAGNOSTIC_OTLP
import io.opentelemetry.sdk.OpenTelemetrySdkBuilder
import kotlinx.coroutines.CoroutineScope
import java.nio.file.Path

internal class OTelConfigurator(
  mainScope: CoroutineScope,
  otelSdkBuilder: OpenTelemetrySdkBuilder,
  appInfo: ApplicationInfo,
  enableMetricsByDefault: Boolean
) : OpenTelemetryDefaultConfigurator(mainScope = mainScope,
                                     otelSdkBuilder = otelSdkBuilder,
                                     serviceName = ApplicationNamesInfo.getInstance().fullProductName,
                                     serviceVersion = appInfo.build.asStringWithoutProductCode(),
                                     serviceNamespace = appInfo.build.productCode,
                                     enableMetricsByDefault = enableMetricsByDefault) {
  @Suppress("SuspiciousCollectionReassignment")
  override fun createSpanExporters(): List<AsyncSpanExporter> {
    var spanExporters = emptyList<AsyncSpanExporter>()
    System.getProperty("idea.diagnostic.opentelemetry.file")?.let { traceFile ->
      spanExporters += (JaegerJsonSpanExporter(file = Path.of(traceFile),
                                               serviceName = serviceName,
                                               serviceVersion = serviceVersion,
                                               serviceNamespace = serviceNamespace))
    }

    System.getProperty(IDEA_DIAGNOSTIC_OTLP)?.let { traceEndpoint ->
      spanExporters += OtlpSpanExporter(traceEndpoint)
    }
    return spanExporters
  }
}