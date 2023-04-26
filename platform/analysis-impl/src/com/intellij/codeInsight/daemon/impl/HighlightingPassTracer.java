// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.diagnostic.telemetry.IJTracer;
import com.intellij.diagnostic.telemetry.TraceManager;
import io.opentelemetry.api.common.AttributeKey;

final class HighlightingPassTracer {
  private HighlightingPassTracer() {
  }

  static IJTracer HIGHLIGHTING_PASS_TRACER = TraceManager.INSTANCE.getTracer("HighlightingPasses");
  static AttributeKey<String> FILE_ATTR_SPAN_KEY = AttributeKey.stringKey("file");
  static AttributeKey<String> CANCELLED_ATTR_SPAN_KEY = AttributeKey.stringKey("cancelled");
}