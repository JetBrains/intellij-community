// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.telemetry

import io.opentelemetry.sdk.metrics.export.MetricExporter
import org.jetbrains.annotations.ApiStatus
import kotlin.time.Duration

@ApiStatus.Internal
data class MetricsExporterEntry(val metrics: List<MetricExporter>, val duration: Duration)
