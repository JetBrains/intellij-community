package org.jetbrains.intellij.build.telemetry

import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SpanExporter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogManager
import java.util.logging.LogRecord
import java.util.logging.Logger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds

@Timeout(20)
class BuildSpanProcessorTest {
  /** Records every call with the thread that made it. */
  private class RecordingExporter : SpanExporter {
    val batches = CopyOnWriteArrayList<List<String>>()
    val threads = CopyOnWriteArrayList<Thread>()
    val flushes = AtomicInteger()
    val shutdowns = AtomicInteger()
    val exported = CountDownLatch(1)

    /** The next `flush` throws once. */
    val failNextFlush = AtomicBoolean()

    /** Every `export` reports a failure through its result code. */
    val failExports = AtomicBoolean()

    override fun export(spans: Collection<SpanData>): CompletableResultCode {
      threads.add(Thread.currentThread())
      batches.add(spans.map { it.name })
      exported.countDown()
      return if (failExports.get()) CompletableResultCode.ofExceptionalFailure(IllegalStateException("The export failed")) else CompletableResultCode.ofSuccess()
    }

    override fun flush(): CompletableResultCode {
      threads.add(Thread.currentThread())
      flushes.incrementAndGet()
      if (failNextFlush.compareAndSet(true, false)) {
        throw IllegalStateException("The flush failed")
      }
      return CompletableResultCode.ofSuccess()
    }

    override fun shutdown(): CompletableResultCode {
      threads.add(Thread.currentThread())
      shutdowns.incrementAndGet()
      return CompletableResultCode.ofSuccess()
    }
  }

  /** Keeps the records of the processor logger. The logger is held strongly, as JUL keeps it weakly. */
  private class RecordingHandler : Handler() {
    val records = CopyOnWriteArrayList<LogRecord>()
    val logger: Logger = Logger.getLogger(BuildSpanProcessor::class.java.name)

    override fun publish(record: LogRecord) {
      records.add(record)
    }

    override fun flush() {}

    override fun close() {}
  }

  private fun <T> withRecordingHandler(block: (RecordingHandler) -> T): T {
    val handler = RecordingHandler()
    handler.logger.addHandler(handler)
    try {
      return block(handler)
    }
    finally {
      handler.logger.removeHandler(handler)
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

  @Test
  fun `an exporter failure keeps the worker alive`() {
    val exporter = RecordingExporter()
    withRecordingHandler { handler ->
      withTracer(exporter, scheduleDelay = 1.hours, maxExportBatchSize = 100) { processor, tracer ->
        exporter.failNextFlush.set(true)
        tracer.spanBuilder("before the failure").startSpan().end()
        processor.flush()
        tracer.spanBuilder("after the failure").startSpan().end()
        processor.flush()
        assertThat(exporter.batches).containsExactly(listOf("before the failure"), listOf("after the failure"))
        assertThat(exporter.flushes.get()).isEqualTo(2)
      }
      assertThat(handler.records).hasSize(1)
      assertThat(handler.records.single().level).isEqualTo(Level.WARNING)
      assertThat(handler.records.single().thrown).hasMessage("The flush failed")
    }
    assertThat(exporter.shutdowns.get()).isEqualTo(1)
  }

  @Test
  fun `a failed result code is a warning`() {
    val exporter = RecordingExporter()
    exporter.failExports.set(true)
    withRecordingHandler { handler ->
      withTracer(exporter, scheduleDelay = 1.hours, maxExportBatchSize = 100) { processor, tracer ->
        tracer.spanBuilder("first").startSpan().end()
        processor.flush()
        tracer.spanBuilder("second").startSpan().end()
        processor.flush()
        // the batch is cleared after a failed export, so the second batch holds only the second span
        assertThat(exporter.batches).containsExactly(listOf("first"), listOf("second"))
      }
      assertThat(handler.records).hasSize(2)
      assertThat(handler.records).allSatisfy { record ->
        assertThat(record.level).isEqualTo(Level.WARNING)
        assertThat(record.thrown).hasMessage("The export failed")
      }
    }
  }

  /**
   * Blocks the first `publish` until [release] opens, then throws from every `publish`. A thrown handler error
   * leaves the log call, so the guard of the worker fails too and the worker ends.
   */
  private class ThrowingHandler(private val release: CountDownLatch) : Handler() {
    val entered = CountDownLatch(1)

    override fun publish(record: LogRecord) {
      entered.countDown()
      release.await()
      throw IllegalStateException("The log handler failed")
    }

    override fun flush() {}

    override fun close() {}
  }

  /**
   * The worker ends with the error of the log handler. The default handler of the JVM collects it here, so the
   * test can assert the reason and the test framework does not report it as an unexpected uncaught error.
   */
  @Test
  fun `flush returns after the worker ended`() {
    val root = LogManager.getLogManager().getLogger("")
    val workerFailures = CopyOnWriteArrayList<Throwable>()
    val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { _, e -> workerFailures.add(e) }
    try {
      repeat(20) { iteration ->
        val exporter = RecordingExporter()
        val release = CountDownLatch(1)
        val handler = ThrowingHandler(release)
        root.addHandler(handler)
        try {
          val processor = BuildSpanProcessor(spanExporters = listOf(exporter), scheduleDelay = 1.hours)
          exporter.failNextFlush.set(true)
          // the first flush makes the exporter throw; the worker logs a warning and blocks in the handler
          val first = Thread.ofVirtual().start { processor.flush() }
          assertThat(handler.entered.await(5, TimeUnit.SECONDS)).isTrue()
          // the second request is queued while the worker is still alive but on its way out:
          // only the final drain of the worker can complete it
          val second = Thread.ofVirtual().start { processor.flush() }
          while (second.state != Thread.State.WAITING) {
            Thread.sleep(1)
          }
          release.countDown()
          first.join(5_000)
          second.join(5_000)
          assertThat(first.isAlive).isFalse()
          assertThat(second.isAlive).isFalse()
          // the worker is gone, so `close` has no worker to wait for and a later `flush` returns at once
          processor.close()
          processor.flush()
          assertThat(exporter.shutdowns.get()).isEqualTo(1)
          // the worker completes the requests before it dies, so its death arrives a moment later
          while (workerFailures.size <= iteration) {
            Thread.sleep(1)
          }
          assertThat(workerFailures.last()).hasMessage("The log handler failed")
        }
        finally {
          release.countDown()
          root.removeHandler(handler)
        }
      }
    }
    finally {
      Thread.setDefaultUncaughtExceptionHandler(previousHandler)
    }
    assertThat(workerFailures).hasSize(20)
  }
}
