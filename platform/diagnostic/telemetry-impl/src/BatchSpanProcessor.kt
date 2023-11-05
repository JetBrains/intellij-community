// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("RAW_RUN_BLOCKING")

package com.intellij.platform.diagnostic.telemetry.impl

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
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.function.BiFunction
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
@Internal
class BatchSpanProcessor(
  coroutineScope: CoroutineScope,
  @JvmField internal val spanExporters: List<AsyncSpanExporter>,
  private val scheduleDelay: Duration = 5.seconds,
  private val maxExportBatchSize: Int = 512,
  private val exporterTimeout: Duration = 30.seconds,
) : SpanProcessor {
  private val queue = Channel<ReadableSpan>(capacity = Channel.UNLIMITED)
  private val flushRequested = Channel<CompletableDeferred<Unit>>(capacity = Channel.UNLIMITED)

  private val processingJob: Job

  init {
    processingJob = coroutineScope.launch {
      val batch = ArrayList<SpanData>(maxExportBatchSize)
      try {
        var counter = 0
        while (true) {
          select {
            flushRequested.onReceive { result ->
              try {
                exportCurrentBatch(batch)
                spanExporters.forEach { it.forceFlush() }
              }
              finally {
                result.complete(Unit)
              }
            }

            queue.onReceive { span ->
              batch.add(span.toSpanData())

              if (counter++ >= maxExportBatchSize) {
                counter = 0
                try {
                  exportCurrentBatch(batch)
                }
                catch (e: CancellationException) {
                  throw e
                }
                catch (e: Throwable) {
                  logger<BatchSpanProcessor>().error("Cannot flush", e)
                }
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
            shutdownExporters()
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
      addSpan(span)
    }
  }

  override fun isEndRequired(): Boolean = true

  override fun shutdown(): CompletableResultCode {
    // shutdown must be performed using scope - explicit shutdown is not required
    return CompletableResultCode.ofSuccess()
  }

  private fun shutdownExporters() {
    for (spanExporter in spanExporters) {
      try {
        spanExporter.shutdown()
      }
      catch (e: Throwable) {
        logger<BatchSpanProcessor>().error("Failed to shutdown", e)
      }
    }
  }

  override fun forceFlush(): CompletableResultCode {
    val completableDeferred = CompletableDeferred<Unit>()
    if (flushRequested.trySend(completableDeferred).isClosed) {
      return CompletableResultCode.ofSuccess()
    }

    val result = CompletableResultCode()
    completableDeferred.asCompletableFuture().handle(BiFunction { _, error ->
      if (error == null) {
        result.succeed()
      }
      else {
        result.fail()
        logger<BatchSpanProcessor>().error("Failed to flush", error)
      }
    })
    return result
  }

  private fun addSpan(span: ReadableSpan) {
    queue.trySend(span)
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

  internal fun flushOtlp(scopeSpans: List<ScopeSpans>) {
    TODO("Not yet implemented")
  }
}
