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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
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
    coroutineScope.launch(Dispatchers.IO) {
      val batch = ArrayList<SpanData>(maxExportBatchSize)
      try {
        while (true) {
          select {
            flushRequested.onReceive { request ->
              try {
                drainQueueInto(batch)
                exportCurrentBatch(batch)
                // Flush exporters even when the batch is empty: a max-size batch export (see `queue.onReceive`
                // below) hands spans to the exporters without flushing them, so JaegerJsonSpanExporter can be
                // left with buffered bytes and a json file that ends mid-span. If the empty batch skipped this,
                // no number of explicit flush() calls would ever finalize that file (AT-1875).
                if (!request.exportOnly) {
                  flushExporters()
                }
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
            drainQueueInto(batch)
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

  /**
   * Moves every span already queued into [batch], so that the export about to happen covers it.
   *
   * [onEnd] only queues; a span reaches [batch] when this processor's own coroutine gets round to receiving it. So a
   * span that ended just before an export - a *root* span in particular, which by construction ends last - is
   * routinely still in the channel, and exporting [batch] alone leaves it out of the file.
   *
   * On the shutdown path there is no later export to pick it up. Cancelling the scope is the documented way to shut
   * this processor down ([forceShutdown]), and the handler for it used to export [batch] and nothing else, so every
   * span still queued was dropped - silently, since a dropped span leaves no trace anywhere. Measured on a dev
   * distribution build writing one trace file per packaging action, that lost the root span of 5 of 8 files.
   */
  private suspend fun drainQueueInto(batch: MutableList<SpanData>) {
    while (true) {
      val span = queue.tryReceive().getOrNull() ?: break
      batch.add(span.toSpanData())
      if (batch.size >= maxExportBatchSize) {
        exportCurrentBatch(batch)
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
      catch (e: Throwable) {
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
      catch (e: Throwable) {
        logger<BatchSpanProcessor>().error("Failed to reset", e)
      }
    }
  }
}
