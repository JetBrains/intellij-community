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

class OTelConfigurator(
  mainScope: CoroutineScope,
  otelSdkBuilder: OpenTelemetrySdkBuilder,
  appInfo: ApplicationInfo,
  enableMetricsByDefault: Boolean
) :
  OpenTelemetryDefaultConfigurator(mainScope = mainScope,
                                   otelSdkBuilder = otelSdkBuilder,
                                   serviceName = ApplicationNamesInfo.getInstance().fullProductName,
                                   serviceVersion = appInfo.build.asStringWithoutProductCode(),
                                   serviceNamespace = appInfo.build.productCode,
                                   enableMetricsByDefault = enableMetricsByDefault) {
  override fun getDefaultSpanExporters(): List<AsyncSpanExporter> {
    super.getDefaultSpanExporters()

    val traceFile = System.getProperty("idea.diagnostic.opentelemetry.file")

    if (traceFile != null) {
      spanExporters.add(JaegerJsonSpanExporter(Path.of(traceFile), serviceName, serviceVersion, serviceNamespace))
    }

    val traceEndpoint = System.getProperty(IDEA_DIAGNOSTIC_OTLP)

    if (traceEndpoint != null) {
      spanExporters.add(OtlpSpanExporter(traceEndpoint))
    }
    return spanExporters
  }
}