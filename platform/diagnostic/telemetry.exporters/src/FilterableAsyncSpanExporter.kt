// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.telemetry.exporters

import com.intellij.platform.diagnostic.telemetry.AsyncSpanExporter
import io.opentelemetry.sdk.trace.data.SpanData
import org.jetbrains.annotations.ApiStatus

/**
 * A general-purpose wrapper for any [com.intellij.platform.diagnostic.telemetry.AsyncSpanExporter] that adds filtering capability.
 * This allows filtering spans before they are passed to the wrapped exporter.
 */
@ApiStatus.Internal
class FilterableAsyncSpanExporter(
  private val delegate: AsyncSpanExporter,
  private val filter: (SpanData) -> Boolean
) : AsyncSpanExporter {

  override suspend fun export(spans: Collection<SpanData>) {
    val filteredSpans = spans.filter(filter)
    if (filteredSpans.isNotEmpty()) delegate.export(filteredSpans)
  }

  override suspend fun flush(): Unit = delegate.flush()

  override suspend fun reset(): Unit = delegate.reset()

  override suspend fun shutdown(): Unit = delegate.shutdown()
}