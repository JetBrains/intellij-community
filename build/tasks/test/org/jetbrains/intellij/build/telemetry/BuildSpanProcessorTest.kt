package org.jetbrains.intellij.build.telemetry

import com.intellij.platform.diagnostic.telemetry.AsyncSpanExporter
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.data.SpanData
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds

@Timeout(20)
class BuildSpanProcessorTest {
  /** Records every call with the thread that made it. */
  private class RecordingExporter : AsyncSpanExporter {
    val batches = CopyOnWriteArrayList<List<String>>()
    val threads = CopyOnWriteArrayList<Thread>()
    val flushes = AtomicInteger()
    val shutdowns = AtomicInteger()
    val exported = CountDownLatch(1)

    override suspend fun export(spans: Collection<SpanData>) {
      threads.add(Thread.currentThread())
      batches.add(spans.map { it.name })
      exported.countDown()
    }

    override suspend fun flush() {
      threads.add(Thread.currentThread())
      flushes.incrementAndGet()
    }

    override suspend fun shutdown() {
      threads.add(Thread.currentThread())
      shutdowns.incrementAndGet()
    }
  }

  private fun <T> withTracer(exporter: RecordingExporter, scheduleDelay: Duration, maxExportBatchSize: Int, block: (BuildSpanProcessor, Tracer) -> T): T {
    return BuildSpanProcessor(spanExporters = listOf(exporter), scheduleDelay = scheduleDelay, maxExportBatchSize = maxExportBatchSize).use { processor ->
      SdkTracerProvider.builder().addSpanProcessor(processor).build().use { provider ->
        block(processor, provider.get("test"))
      }
    }
  }

  @Test
  fun `a full batch exports without a flush`() {
    val exporter = RecordingExporter()
    withTracer(exporter, scheduleDelay = 1.hours, maxExportBatchSize = 2) { _, tracer ->
      tracer.spanBuilder("first").startSpan().end()
      tracer.spanBuilder("second").startSpan().end()
      assertThat(exporter.exported.await(5, TimeUnit.SECONDS)).isTrue()
      assertThat(exporter.batches).containsExactly(listOf("first", "second"))
    }
  }

  @Test
  fun `the delay exports a partial batch and flushes the exporter`() {
    val exporter = RecordingExporter()
    withTracer(exporter, scheduleDelay = 50.milliseconds, maxExportBatchSize = 100) { _, tracer ->
      tracer.spanBuilder("only").startSpan().end()
      assertThat(exporter.exported.await(5, TimeUnit.SECONDS)).isTrue()
      while (exporter.flushes.get() == 0) {
        Thread.sleep(10)
      }
      assertThat(exporter.batches).containsExactly(listOf("only"))
    }
  }

  @Test
  fun `flush returns after the export of the spans that ended before it`() {
    val exporter = RecordingExporter()
    withTracer(exporter, scheduleDelay = 1.hours, maxExportBatchSize = 100) { processor, tracer ->
      tracer.spanBuilder("before").startSpan().end()
      processor.flush()
      assertThat(exporter.batches).containsExactly(listOf("before"))
      assertThat(exporter.flushes.get()).isEqualTo(1)
    }
  }

  @Test
  fun `close drains the queue and shuts the exporter down once`() {
    val exporter = RecordingExporter()
    val processor = BuildSpanProcessor(spanExporters = listOf(exporter), scheduleDelay = 1.hours)
    val tracer = SdkTracerProvider.builder().addSpanProcessor(processor).build().get("test")
    tracer.spanBuilder("late").startSpan().end()
    processor.close()
    processor.close()
    assertThat(exporter.batches).containsExactly(listOf("late"))
    assertThat(exporter.shutdowns.get()).isEqualTo(1)
    // a span that ends after the close is dropped, and the call does not block
    tracer.spanBuilder("dropped").startSpan().end()
    processor.flush()
    assertThat(exporter.batches).containsExactly(listOf("late"))
  }

  @Test
  fun `every exporter call runs on the virtual thread of the processor`() {
    val exporter = RecordingExporter()
    withTracer(exporter, scheduleDelay = 1.hours, maxExportBatchSize = 100) { processor, tracer ->
      tracer.spanBuilder("span").startSpan().end()
      processor.flush()
    }
    assertThat(exporter.threads).isNotEmpty
    assertThat(exporter.threads).allSatisfy { thread ->
      assertThat(thread.isVirtual).isTrue()
      assertThat(thread.name).isEqualTo("build spans")
    }
  }
}
