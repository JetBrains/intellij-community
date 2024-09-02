// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import org.jetbrains.annotations.TestOnly
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
@Internal
class BatchSpanProcessor(
  private val coroutineScope: CoroutineScope,
  private val spanExporters: List<AsyncSpanExporter>,
  private val scheduleDelay: Duration = 1.minutes,
  private val maxExportBatchSize: Int = 512,
) : SpanProcessor {
  private val queue = Channel<ReadableSpan>(capacity = Channel.UNLIMITED)
  private val flushRequested = Channel<FlushRequest>(capacity = Channel.UNLIMITED)

  private data class FlushRequest(@JvmField val exportOnly: Boolean) {
    @JvmField
    val job: CompletableDeferred<Unit> = CompletableDeferred()
  }

  init {
    coroutineScope.launch {
      val batch = ArrayList<SpanData>(maxExportBatchSize)
      try {
        while (true) {
          select {
            flushRequested.onReceive { request ->
              try {
                val isExported = exportCurrentBatch(batch)
                if (isExported && !request.exportOnly) {
                  flushExporters()
                }
                Unit
              }
              finally {
                request.job.complete(Unit)
              }
            }
            queue.onReceive { span ->
              batch.add(span.toSpanData())
              if (batch.size >= maxExportBatchSize) {
                exportCurrentBatch(batch)
              }
            }

            // or if no new spans for a while, flush buffer
            onTimeout(scheduleDelay) {
              if (exportCurrentBatch(batch)) {
                flushExporters()
              }
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

  private suspend fun flushExporters() {
    for (spanExporter in spanExporters) {
      try {
        withTimeout(10.seconds) {
          spanExporter.flush()
        }
      }
      catch (e: CancellationException) {
        throw e
      }
      catch (e: Exception) {
        logger<BatchSpanProcessor>().error("Failed to flush", e)
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

  // shutdown must be performed using scope - explicit shutdown is not required
  suspend fun forceShutdown() {
    coroutineScope.coroutineContext.job.cancelAndJoin()
  }

  override fun shutdown(): CompletableResultCode {
    // shutdown must be performed using scope - explicit shutdown is not required
    return CompletableResultCode.ofSuccess()
  }

  suspend fun flush() {
    val flushRequest = FlushRequest(exportOnly = false)
    if (!flushRequested.trySend(flushRequest).isClosed) {
      flushRequest.job.join()
    }
  }

  suspend fun scheduleFlush() {
    flushRequested.send(FlushRequest(exportOnly = true))
  }

  override fun forceFlush(): CompletableResultCode {
    throw UnsupportedOperationException()
  }

  private suspend fun exportCurrentBatch(batch: MutableList<SpanData>): Boolean {
    if (batch.isEmpty()) {
      return false
    }

    try {
      for (spanExporter in spanExporters) {
        withTimeoutOrNull(30.seconds) {
          spanExporter.export(batch)
        }
      }
      return true
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
    return false
  }

  suspend fun flushOtlp(scopeSpans: Collection<ScopeSpans>) {
    for (spanExporter in spanExporters) {
      if (spanExporter is JaegerJsonSpanExporter) {
        spanExporter.flushOtlp(scopeSpans)
        break
      }
    }
  }

  @TestOnly
  suspend fun reset() {
    for (spanExporter in spanExporters) {
      try {
        withTimeout(30.seconds) {
          spanExporter.reset()
        }
      }
      catch (e: CancellationException) {
        throw e
      }
      catch (e: Exception) {
        logger<BatchSpanProcessor>().error("Failed to reset", e)
      }
    }
  }
}
