package org.jetbrains.intellij.build.telemetry

import com.intellij.platform.diagnostic.telemetry.AsyncSpanExporter
import com.intellij.platform.diagnostic.telemetry.exporters.JaegerJsonSpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.data.SpanData
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@Timeout(20)
class TraceManagerTest {
  @Test
  fun `the trace file is complete when the block returns`(@TempDir directory: Path) {
    val file = directory.resolve("trace.json")
    val result = withSpanProcessor(listOf(JaegerJsonSpanExporter(file = file, serviceName = "test"))) { processor ->
      SdkTracerProvider.builder().addSpanProcessor(processor).build().use { provider ->
        provider.get("test").spanBuilder("root span").startSpan().end()
      }
      42
    }
    assertThat(result).isEqualTo(42)
    val trace = Files.readString(file)
    Json.parseToJsonElement(trace)
    assertThat(trace).contains("root span")
  }

  @Test
  fun `an empty block still closes its exporter`() {
    val closed = AtomicInteger()
    val exporter = object : AsyncSpanExporter {
      override suspend fun export(spans: Collection<SpanData>) {}

      override suspend fun shutdown() {
        closed.incrementAndGet()
      }
    }
    repeat(100) {
      withSpanProcessor(listOf(exporter)) {}
    }
    assertThat(closed.get()).isEqualTo(100)
  }

  @Test
  fun `an interrupted caller still closes the trace file`(@TempDir directory: Path) {
    val file = directory.resolve("trace.json")
    val exporter = JaegerJsonSpanExporter(file = file, serviceName = "test")
    try {
      Thread.currentThread().interrupt()
      runTelemetryCleanup { runBlocking { exporter.shutdown() } }
      assertThat(Thread.currentThread().isInterrupted).isTrue()
    }
    finally {
      Thread.interrupted()
    }
    Json.parseToJsonElement(Files.readString(file))
  }

  @Test
  fun `cleanup runs once and preserves its failure despite caller interrupts`() {
    val entered = CountDownLatch(1)
    val release = CountDownLatch(1)
    val calls = AtomicInteger()
    val failure = InterruptedException("The cleanup failed")
    val result = CompletableFuture<Pair<Throwable?, Boolean>>()
    val worker = Thread.ofVirtual().start {
      Thread.currentThread().interrupt()
      val actual = runCatching {
        runTelemetryCleanup {
          calls.incrementAndGet()
          entered.countDown()
          release.await()
          throw failure
        }
      }.exceptionOrNull()
      result.complete(actual to Thread.currentThread().isInterrupted)
    }
    try {
      assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue()
      worker.interrupt()
      assertThat(result.isDone).isFalse()
    }
    finally {
      release.countDown()
      worker.join(5000)
    }
    val actual = result.get(5, TimeUnit.SECONDS)
    assertThat(actual.first).isSameAs(failure)
    assertThat(actual.second).isTrue()
    assertThat(calls.get()).isEqualTo(1)
    assertThat(worker.isAlive).isFalse()
  }

  @Test
  fun `shutdown waits for exporters and preserves the build failure and interrupts`() {
    val closing = CountDownLatch(1)
    val release = CompletableDeferred<Unit>()
    val closed = AtomicInteger()
    val failure = IllegalStateException("The build failed")
    val result = CompletableFuture<Pair<Throwable?, Boolean>>()
    val exporter = object : AsyncSpanExporter {
      override suspend fun export(spans: Collection<SpanData>) {}

      override suspend fun shutdown() {
        closing.countDown()
        release.await()
        closed.incrementAndGet()
      }
    }
    val worker = Thread.ofVirtual().start {
      val actual = runCatching {
        withSpanProcessor(listOf(exporter)) {
          Thread.currentThread().interrupt()
          throw failure
        }
      }.exceptionOrNull()
      result.complete(actual to Thread.currentThread().isInterrupted)
    }
    try {
      assertThat(closing.await(5, TimeUnit.SECONDS)).isTrue()
      worker.interrupt()
      assertThat(result.isDone).isFalse()
      assertThat(closed.get()).isZero()
    }
    finally {
      release.complete(Unit)
      worker.join(5000)
    }
    val actual = result.get(5, TimeUnit.SECONDS)
    assertThat(actual.first).isSameAs(failure)
    assertThat(actual.second).isTrue()
    assertThat(closed.get()).isEqualTo(1)
    assertThat(worker.isAlive).isFalse()
  }
}
