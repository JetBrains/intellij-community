// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.platform.diagnostic.telemetry.IJTracer;
import com.intellij.platform.diagnostic.telemetry.TelemetryTracer;
import io.opentelemetry.api.common.AttributeKey;

import static com.intellij.codeInsight.daemon.impl.HighlightingPassesScopeKt.*;

final class HighlightingPassTracer {
  private HighlightingPassTracer() {
  }

  static IJTracer HIGHLIGHTING_PASS_TRACER = TelemetryTracer.Companion.getInstance().getTracer(HighlightingPasses);
  static AttributeKey<String> FILE_ATTR_SPAN_KEY = AttributeKey.stringKey("file");
  static AttributeKey<String> CANCELLED_ATTR_SPAN_KEY = AttributeKey.stringKey("cancelled");
}