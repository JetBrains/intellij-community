// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.telemetry

import com.intellij.platform.buildScripts.concurrency.TaskSignal
import com.intellij.platform.buildScripts.concurrency.awaitUninterruptibly
import io.opentelemetry.context.Context
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.trace.ReadWriteSpan
import io.opentelemetry.sdk.trace.ReadableSpan
import io.opentelemetry.sdk.trace.SpanProcessor
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SpanExporter
import org.jetbrains.annotations.ApiStatus
import java.lang.System.Logger
import java.util.concurrent.CompletableFuture
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Exports the ended spans in batches from one virtual thread.
 *
 * The platform `BatchSpanProcessor` runs its loop as a coroutine on a dispatcher thread. A platform thread that
 * loads a class beside the build workers can take part in the deadlock of JDK-8369019, so the build uses a virtual
 * thread instead. The exporters are blocking [SpanExporter]s, and the worker calls them directly. The worker waits
 * at most [exportTimeout] for the result of one exporter call.
 *
 * A failed exporter call is a warning. The worker logs it and goes on with the next batch. The build does not fail
 * because of a lost span.
 *
 * [onEnd] never blocks. [flush] waits for the export of the spans that ended before the call, and returns when the
 * worker has ended. [close] exports the pending spans, shuts the exporters down and returns when the worker has
 * ended, also for an interrupted caller.
 */
@ApiStatus.Internal
class BuildSpanProcessor(
  private val spanExporters: List<SpanExporter>,
  private val scheduleDelay: Duration = 1.minutes,
  private val maxExportBatchSize: Int = 512,
  private val exportTimeout: Duration = 30.seconds,
) : SpanProcessor, AutoCloseable {
  private val queue = LinkedBlockingDeque<Any>()
  private val closed = AtomicBoolean()
  private val worker: Thread = Thread.ofVirtual().name("build spans").unstarted(::run)
  private val finished = CompletableFuture<Unit>()

  private class FlushRequest(@JvmField val exportOnly: Boolean) {
    @JvmField
    val done: TaskSignal<Unit> = TaskSignal("flush spans")
  }

  private object Shutdown

  init {
    worker.start()
  }

  private fun run() {
    val batch = ArrayList<SpanData>(maxExportBatchSize)
    var exportersShutDown = false
    try {
      while (true) {
        val item = queue.poll(scheduleDelay.inWholeNanoseconds, TimeUnit.NANOSECONDS)
        try {
          when (item) {
            null -> {
              if (exportBatch(batch)) {
                flushExporters()
              }
            }
            is ReadableSpan -> {
              batch.add(item.toSpanData())
              if (batch.size >= maxExportBatchSize) {
                exportBatch(batch)
              }
            }
            is FlushRequest -> {
              try {
                drainQueue(batch)
                exportBatch(batch)
                if (!item.exportOnly) {
                  flushExporters()
                }
              }
              finally {
                item.done.complete(Unit)
              }
            }
            Shutdown -> {
              try {
                drainQueue(batch)
                exportBatch(batch)
              }
              finally {
                exportersShutDown = true
                shutdownExporters()
              }
              return
            }
          }
        }
        catch (e: Throwable) {
          logger.log(Logger.Level.WARNING, "Failed to process the span queue", e)
        }
      }
    }
    finally {
      // the order matters for `flush`: a caller that sees `finished` done also sees `closed` set,
      // and the queue drain below runs after `finished` is done
      closed.set(true)
      try {
        if (!exportersShutDown) {
          shutdownExporters()
        }
      }
      finally {
        finished.complete(Unit)
        completePendingFlushRequests()
      }
    }
  }

  /** Moves the queued spans into the batch. A queued flush request stays for the loop. */
  private fun drainQueue(batch: MutableList<SpanData>) {
    val kept = ArrayList<Any>()
    while (true) {
      val item = queue.poll() ?: break
      if (item is ReadableSpan) {
        batch.add(item.toSpanData())
        if (batch.size >= maxExportBatchSize) {
          exportBatch(batch)
        }
      }
      else {
        kept.add(item)
      }
    }
    // the requests go back in their order, ahead of the spans that arrive from now on
    for (item in kept.asReversed()) {
      queue.addFirst(item)
    }
  }

  private fun completePendingFlushRequests() {
    while (true) {
      val item = queue.poll() ?: break
      if (item is FlushRequest) {
        item.done.complete(Unit)
      }
    }
  }

  /** Returns `true` when the batch held spans. The batch is empty on return. */
  private fun exportBatch(batch: MutableList<SpanData>): Boolean {
    if (batch.isEmpty()) {
      return false
    }
    try {
      for (spanExporter in spanExporters) {
        callExporter("export spans", spanExporter) { it.export(batch) }
      }
    }
    finally {
      batch.clear()
    }
    return true
  }

  private fun flushExporters() {
    for (spanExporter in spanExporters) {
      callExporter("flush spans", spanExporter) { it.flush() }
    }
  }

  private fun shutdownExporters() {
    for (spanExporter in spanExporters) {
      callExporter("shut a span exporter down", spanExporter) { it.shutdown() }
    }
  }

  /** Waits at most [exportTimeout] for the result. A failure, a timeout or a thrown error is a warning. */
  private inline fun callExporter(action: String, exporter: SpanExporter, call: (SpanExporter) -> CompletableResultCode) {
    try {
      val result = call(exporter).join(exportTimeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
      if (!result.isDone) {
        logger.log(Logger.Level.WARNING, "Failed to $action: $exporter did not complete in $exportTimeout")
      }
      else if (!result.isSuccess) {
        logger.log(Logger.Level.WARNING, "Failed to $action: $exporter reported a failure", result.failureThrowable)
      }
    }
    catch (e: Throwable) {
      logger.log(Logger.Level.WARNING, "Failed to $action: $exporter threw an error", e)
    }
  }

  override fun onStart(parentContext: Context, span: ReadWriteSpan) {
  }

  override fun isStartRequired(): Boolean = false

  /** After [close] the worker is gone, so a span that ends later is dropped. */
  override fun onEnd(span: ReadableSpan) {
    if (span.spanContext.isSampled && !closed.get()) {
      queue.add(span)
    }
  }

  override fun isEndRequired(): Boolean = true

  /**
   * Exports the spans that ended before the call, flushes the exporters, and blocks until both are done.
   *
   * The call returns when the worker has ended, also for a request that the worker did not see any more.
   */
  fun flush() {
    if (closed.get()) {
      return
    }
    val request = FlushRequest(exportOnly = false)
    queue.add(request)
    // the worker completes `finished` before it drains the queue, so a request that
    // the worker cannot see any more is drained here
    if (finished.isDone) {
      completePendingFlushRequests()
    }
    request.done.await()
  }

  /** Asks for an export of the ended spans. The call returns at once. */
  fun scheduleFlush() {
    if (!closed.get()) {
      queue.add(FlushRequest(exportOnly = true))
    }
  }

  override fun forceFlush(): CompletableResultCode {
    flush()
    return CompletableResultCode.ofSuccess()
  }

  /** The processor stops through [close]. The tracer provider does not own it. */
  override fun shutdown(): CompletableResultCode = CompletableResultCode.ofSuccess()

  /** Waits for the worker despite caller interruptions, and keeps the interrupt status of the caller. */
  override fun close() {
    if (closed.compareAndSet(false, true)) {
      queue.add(Shutdown)
    }
    finished.awaitUninterruptibly()
  }

  private companion object {
    val logger: Logger = System.getLogger(BuildSpanProcessor::class.java.name)
  }
}
