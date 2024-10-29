// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.platform.diagnostic.telemetry.IJTracer
import com.intellij.platform.diagnostic.telemetry.Scope
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import io.opentelemetry.api.common.AttributeKey
import org.jetbrains.annotations.ApiStatus.Internal

/**
 * Telemetry constants for daemon passes
 */
@Internal
object HighlightingPassTracer {
  @JvmField
  val HIGHLIGHTING_PASS_TRACER: IJTracer = TelemetryManager.getTracer(Scope("HighlightingPasses"))
  @JvmField
  val FILE_ATTR_SPAN_KEY: AttributeKey<String> = AttributeKey.stringKey("file")
  @JvmField
  val CANCELLED_ATTR_SPAN_KEY: AttributeKey<String> = AttributeKey.stringKey("cancelled")
}