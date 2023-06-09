// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.platform.diagnostic.telemetry.IJTracer;
import com.intellij.platform.diagnostic.telemetry.Scope;
import com.intellij.platform.diagnostic.telemetry.TelemetryTracer;
import io.opentelemetry.api.common.AttributeKey;

/**
 * Telemetry constants for daemon passes
 */
final class HighlightingPassTracer {
  static final IJTracer HIGHLIGHTING_PASS_TRACER = TelemetryTracer.getInstance().getTracer(new Scope("HighlightingPasses", null));
  static final AttributeKey<String> FILE_ATTR_SPAN_KEY = AttributeKey.stringKey("file");
  static final AttributeKey<String> CANCELLED_ATTR_SPAN_KEY = AttributeKey.stringKey("cancelled");
}