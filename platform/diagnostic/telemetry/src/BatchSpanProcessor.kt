// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic.telemetry

import com.intellij.openapi.diagnostic.Logger
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.metrics.LongCounter
import io.opentelemetry.api.metrics.MeterProvider
import io.opentelemetry.context.Context
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.trace.ReadWriteSpan
import io.opentelemetry.sdk.trace.ReadableSpan
import io.opentelemetry.sdk.trace.SpanProcessor
import io.opentelemetry.sdk.trace.data.SpanData
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.future.asCompletableFuture
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.LongAdder
import java.util.function.BiFunction
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.time.toDuration

private val SPAN_PROCESSOR_TYPE_LABEL = AttributeKey.stringKey("spanProcessorType")
private val SPAN_PROCESSOR_DROPPED_LABEL = AttributeKey.booleanKey("dropped")
private val SPAN_PROCESSOR_TYPE_VALUE = BatchSpanProcessor::class.java.simpleName

interface AsyncSpanExporter {
  suspend fun export(spans: Collection<SpanData>)

  fun shutdown() {
  }
}

@Internal
class BatchSpanProcessor(
  mainScope: CoroutineScope,
  private val spanExporters: List<AsyncSpanExporter>,
  meterProvider: MeterProvider = MeterProvider.noop(),
  private val scheduleDelay: Duration = 5.seconds,
  maxQueueSize: Int = 2048,
  private val maxExportBatchSize: Int = 512,
  private val exporterTimeout: Duration = 30.seconds,
) : SpanProcessor {
  private val isShutdown = AtomicBoolean(false)

  private val queue = Channel<ReadableSpan>(maxQueueSize)
  private val processedSpanCounter: LongCounter
  private val droppedAttrs: Attributes
  private val exportedAttrs: Attributes
  private var nextExportTime: Duration = Duration.ZERO

  private val queueSize = LongAdder()

  // When waiting on the spans queue, exporter thread sets this atomic to the number of more
  // spans it needs before doing an export. Writer threads would then wait for the queue to reach
  // spansNeeded size before notifying the exporter thread about new entries.
  // Integer.MAX_VALUE is used to imply that exporter thread is not expecting any signal. Since
  // exporter thread doesn't expect any signal initially, this value is initialized to
  // Integer.MAX_VALUE.
  private val spansNeeded = AtomicInteger(Int.MAX_VALUE)
  private val signal = Channel<Boolean>(1)
  private val flushRequested = AtomicReference<CompletableDeferred<Unit>?>()

  @Volatile
  private var continueWork = true
  private val processingJob: Job

  init {
    val meter = meterProvider.meterBuilder("io.opentelemetry.sdk.trace").build()
    meter
      .gaugeBuilder("queueSize")
      .ofLongs()
      .setDescription("The number of spans queued")
      .setUnit("1")
      .buildWithCallback { result ->
        result.record(queueSize.sum(), Attributes.of(SPAN_PROCESSOR_TYPE_LABEL, SPAN_PROCESSOR_TYPE_VALUE))
      }
    processedSpanCounter = meter
      .counterBuilder("processedSpans")
      .setUnit("1")
      .setDescription("The number of spans processed by the BatchSpanProcessor. [dropped=true if they were dropped due to high throughput]")
      .build()
    droppedAttrs = Attributes.of(
      SPAN_PROCESSOR_TYPE_LABEL,
      SPAN_PROCESSOR_TYPE_VALUE,
      SPAN_PROCESSOR_DROPPED_LABEL,
      true)
    exportedAttrs = Attributes.of(
      SPAN_PROCESSOR_TYPE_LABEL,
      SPAN_PROCESSOR_TYPE_VALUE,
      SPAN_PROCESSOR_DROPPED_LABEL,
      false)

    processingJob = mainScope.launch(Dispatchers.IO) {
      processQueue()
    }
  }

  override fun onStart(parentContext: Context, span: ReadWriteSpan) {
  }

  override fun isStartRequired() = false

  override fun onEnd(span: ReadableSpan) {
    if (span.spanContext.isSampled) {
      addSpan(span)
    }
  }

  override fun isEndRequired() = true

  override fun shutdown(): CompletableResultCode {
    if (isShutdown.getAndSet(true)) {
      return CompletableResultCode.ofSuccess()
    }

    continueWork = false
    runBlocking(NonCancellable) {
      try {
        withTimeout(1.minutes) {
          processingJob.join()

          val batch = ArrayList<SpanData>(maxExportBatchSize)
          while (true) {
            val span = queue.tryReceive().getOrNull() ?: break
            queueSize.decrement()
            batch.add(span.toSpanData())
          }
          exportCurrentBatch(batch)
        }
      }
      finally {
        withContext(NonCancellable) {
          for (spanExporter in spanExporters) {
            try {
              spanExporter.shutdown()
            }
            catch (e: Throwable) {
              Logger.getInstance(BatchSpanProcessor::class.java.name).error("Failed to shutdown", e)
            }
          }
        }
      }
    }
    return CompletableResultCode.ofSuccess()
  }

  override fun forceFlush(): CompletableResultCode {
    // we set the atomic here to trigger the worker loop to do a flush of the entire queue
    val completableDeferred = CompletableDeferred<Unit>()
    if (flushRequested.compareAndSet(null, CompletableDeferred())) {
      signal.trySend(true)
    }
    val result = CompletableResultCode()
    completableDeferred.asCompletableFuture().handle(BiFunction { _, error ->
      if (error == null) {
        result.succeed()
      }
      else {
        result.fail()
        Logger.getInstance(BatchSpanProcessor::class.java.name).error("Failed to flush", error)
      }
    })
    return result
  }

  private fun addSpan(span: ReadableSpan) {
    if (queue.trySend(span).isSuccess) {
      queueSize.increment()
      if (queueSize.sum() >= spansNeeded.get()) {
        signal.trySend(true)
      }
    }
    else {
      processedSpanCounter.add(1, droppedAttrs)
    }
  }

  private suspend fun processQueue() {
    updateNextExportTime()
    val batch = ArrayList<SpanData>(maxExportBatchSize)
    while (continueWork) {
      val flushRequestJob = flushRequested.get()
      if (flushRequestJob != null) {
        try {
          flush(batch)
        }
        finally {
          flushRequested.compareAndSet(flushRequestJob, null)
          flushRequestJob.complete(Unit)
        }
      }

      val limit = maxExportBatchSize - batch.size
      var polledCount = 0
      while (polledCount++ < limit) {
        val span = queue.tryReceive().getOrNull() ?: break
        queueSize.decrement()
        batch.add(span.toSpanData())
      }

      if (batch.size >= maxExportBatchSize || System.nanoTime().toDuration(DurationUnit.NANOSECONDS) >= nextExportTime) {
        exportCurrentBatch(batch)
        updateNextExportTime()
      }

      if (queueSize.sum() == 0L) {
        val pollWaitTime = nextExportTime - System.nanoTime().toDuration(DurationUnit.NANOSECONDS)
        if (pollWaitTime > Duration.ZERO) {
          spansNeeded.set(maxExportBatchSize - batch.size)
          withTimeoutOrNull(pollWaitTime) {
            signal.receive()
          }
          spansNeeded.set(Int.MAX_VALUE)
        }
      }
    }
  }

  private suspend fun flush(batch: MutableList<SpanData>) {
    var spansToFlush = queueSize.sum()
    while (spansToFlush > 0) {
      val span = queue.tryReceive().getOrNull() ?: break
      queueSize.decrement()
      batch.add(span.toSpanData())
      spansToFlush--
      if (batch.size >= maxExportBatchSize) {
        exportCurrentBatch(batch)
      }
    }

    exportCurrentBatch(batch)
  }

  private fun updateNextExportTime() {
    nextExportTime = scheduleDelay + System.nanoTime().toDuration(DurationUnit.NANOSECONDS)
  }

  private suspend fun exportCurrentBatch(batch: MutableList<SpanData>) {
    if (batch.isEmpty()) {
      return
    }

    try {
      for (spanExporter in spanExporters) {
        withTimeout(exporterTimeout) {
          spanExporter.export(batch)
        }
        processedSpanCounter.add(batch.size.toLong(), exportedAttrs)
      }
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: Throwable) {
      Logger.getInstance(BatchSpanProcessor::class.java.name).error("Failed to export", e)
    }
    finally {
      batch.clear()
    }
  }
}
