// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("RAW_RUN_BLOCKING")

package com.intellij.platform.diagnostic.telemetry.exporters

import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.diagnostic.telemetry.AsyncSpanExporter
import io.opentelemetry.context.Context
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.trace.ReadWriteSpan
import io.opentelemetry.sdk.trace.ReadableSpan
import io.opentelemetry.sdk.trace.SpanProcessor
import io.opentelemetry.sdk.trace.data.SpanData
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select
import org.jetbrains.annotations.ApiStatus.Internal
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
@Internal
class BatchSpanProcessor(
  coroutineScope: CoroutineScope,
  private val spanExporters: List<AsyncSpanExporter>,
  private val scheduleDelay: Duration = 1.minutes,
  private val maxExportBatchSize: Int = 512,
  private val exporterTimeout: Duration = 30.seconds,
) : SpanProcessor {
  private val queue = Channel<ReadableSpan>(capacity = Channel.UNLIMITED)
  init {
    coroutineScope.launch {
      val batch = ArrayList<SpanData>(maxExportBatchSize)
      try {
        while (true) {
          select {
            queue.onReceive { span ->
              batch.add(span.toSpanData())

              if (batch.size >= maxExportBatchSize) {
                exportCurrentBatch(batch)
              }
            }

            // or if no new spans for a while, flush buffer
            onTimeout(scheduleDelay) {
              exportCurrentBatch(batch)
            }
          }
        }
      }
      catch (e: CancellationException) {
        withContext(NonCancellable) {
          try {
            exportCurrentBatch(batch)
          }
          finally {
            for (spanExporter in spanExporters) {
              try {
                spanExporter.shutdown()
              }
              catch (e: Throwable) {
                logger<BatchSpanProcessor>().error("Failed to shutdown", e)
              }
            }
          }
        }
        throw e
      }
    }
  }

  override fun onStart(parentContext: Context, span: ReadWriteSpan) {
  }

  override fun isStartRequired(): Boolean = false

  override fun onEnd(span: ReadableSpan) {
    if (span.spanContext.isSampled) {
      queue.trySend(span)
    }
  }

  override fun isEndRequired(): Boolean = true

  override fun shutdown(): CompletableResultCode {
    // shutdown must be performed using scope - explicit shutdown is not required
    return CompletableResultCode.ofSuccess()
  }

  override fun forceFlush(): CompletableResultCode {
    throw UnsupportedOperationException()
  }

  private suspend fun exportCurrentBatch(batch: MutableList<SpanData>) {
    if (batch.isEmpty()) {
      return
    }

    try {
      for (spanExporter in spanExporters) {
        withTimeoutOrNull(exporterTimeout) {
          spanExporter.export(batch)
        }
      }
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: Throwable) {
      logger<BatchSpanProcessor>().error("Failed to export", e)
    }
    finally {
      batch.clear()
    }
  }

  suspend fun flushOtlp(scopeSpans: Collection<ScopeSpans>) {
    for (spanExporter in spanExporters) {
      if (spanExporter is JaegerJsonSpanExporter) {
        spanExporter.flushOtlp(scopeSpans)
        break
      }
    }
  }
}
