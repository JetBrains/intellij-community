// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.telemetry.exporters.meters.models

import com.fasterxml.jackson.annotation.JsonIgnore
import io.opentelemetry.sdk.metrics.data.ExemplarData

/**
 * Jackson mixin for ignoring some fields during serialization.
 * Counterpart of io.opentelemetry.sdk.metrics.data.PointData
 */
internal abstract class PointDataMixIn {
  @JsonIgnore
  abstract fun getExemplars(): List<ExemplarData>
}