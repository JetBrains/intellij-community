// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.telemetry

import io.opentelemetry.sdk.trace.data.SpanData
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface AsyncSpanExporter {
  suspend fun export(spans: Collection<SpanData>)

  suspend fun flush() {}

  /** Should discard any previously exported metrics */
  suspend fun reset() {}

  suspend fun shutdown() {}
}