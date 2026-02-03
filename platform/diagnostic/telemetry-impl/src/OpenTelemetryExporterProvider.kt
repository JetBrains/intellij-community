// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.telemetry.impl

import com.intellij.platform.diagnostic.telemetry.AsyncSpanExporter
import io.opentelemetry.sdk.metrics.export.MetricExporter
import org.jetbrains.annotations.ApiStatus.Internal
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * EP for custom spans and metrics providers. These providers will be loaded after the application startup.
 * By this time, default platform providers will be already added.
 *
 * Only bundled plugins can provide it.
 */
@Internal
interface OpenTelemetryExporterProvider {
  fun getSpanExporters(): List<AsyncSpanExporter> = emptyList()

  fun getMetricsExporters(): List<MetricExporter>

  fun getReadInterval(): Duration = 1.minutes
}