// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.telemetry

import com.intellij.platform.diagnostic.telemetry.exporters.BatchSpanProcessor
import com.intellij.platform.diagnostic.telemetry.exporters.JaegerJsonSpanExporter
import io.kotest.matchers.shouldBe
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.data.SpanData
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.Collections

class BatchSpanProcessorTest {
  /**
   * A max-size batch export hands spans to [JaegerJsonSpanExporter] without flushing it, so the json file on
   * disk can end mid-span. An explicit [BatchSpanProcessor.flush] used to skip flushing the exporters whenever
   * its own batch happened to be empty — leaving that file truncated no matter how many times a reader
   * requested a flush (AT-1875). A flush request must finalize the file regardless of pending spans.
   */
  @Test
  fun `explicit flush finalizes the json file even when no spans are pending`(@TempDir directory: Path): Unit = runBlocking {
    val file = directory.resolve("trace.json")
    val exporter = JaegerJsonSpanExporter(file = file, serviceName = "test")
    @Suppress("RAW_SCOPE_CREATION")
    val processor = BatchSpanProcessor(coroutineScope = CoroutineScope(SupervisorJob()), spanExporters = listOf(exporter))
    try {
      processor.flush()

      Files.readString(file).trimEnd().endsWith("]}]}") shouldBe true
    }
    finally {
      processor.forceShutdown()
    }
  }

  /**
   * [BatchSpanProcessor.onEnd] only queues a span; it reaches the batch when the processor's own coroutine gets round
   * to receiving it. So a span that ends while an export is in flight is still in the *queue*, and the shutdown that
   * follows used to export the batch alone and drop the queue with the coroutine - silently, since a dropped span
   * leaves no trace anywhere.
   *
   * The span that ends last is a *root* span, so on a build that writes one trace file per action this cost most of
   * those files their root: measured at 5 of 8 on a dev distribution build, and a file with no root span has no total
   * for the work it describes.
   *
   * The interleaving is forced rather than hoped for. With one span per batch the first span puts the processor inside
   * `export` and an exporter that never returns holds it there, so when the second span ends there is no receiver
   * waiting on the queue and it can only be buffered. Shutting down from there is the case that used to lose it.
   */
  @Test
  fun `a span that ends while an export is in flight survives the shutdown`(): Unit = runBlocking {
    val exportStarted = CompletableDeferred<Unit>()
    val neverReleased = CompletableDeferred<Unit>()
    val exported = Collections.synchronizedList(ArrayList<String>())
    val exporter = object : AsyncSpanExporter {
      override suspend fun export(spans: Collection<SpanData>) {
        spans.mapTo(exported) { it.name }
        // Only the first export blocks; the one the shutdown itself performs has to be able to finish.
        if (exportStarted.complete(Unit)) {
          neverReleased.await()
        }
      }
    }

    @Suppress("RAW_SCOPE_CREATION")
    val processor = BatchSpanProcessor(
      coroutineScope = CoroutineScope(SupervisorJob()),
      spanExporters = listOf(exporter),
      maxExportBatchSize = 1,
    )
    val tracer = SdkTracerProvider.builder().addSpanProcessor(processor).build().get("test")

    tracer.spanBuilder("first").startSpan().end()
    exportStarted.await()
    tracer.spanBuilder("last").startSpan().end()

    processor.forceShutdown()

    exported shouldBe listOf("first", "last")
  }
}
