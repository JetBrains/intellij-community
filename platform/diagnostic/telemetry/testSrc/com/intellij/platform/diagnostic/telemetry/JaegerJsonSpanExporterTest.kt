// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.telemetry

import com.intellij.platform.diagnostic.telemetry.exporters.JaegerJsonSpanExporter
import io.kotest.matchers.shouldBe
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.sdk.common.InstrumentationLibraryInfo
import io.opentelemetry.sdk.common.InstrumentationScopeInfo
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.data.EventData
import io.opentelemetry.sdk.trace.data.LinkData
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.data.StatusData
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.fileSize

class JaegerJsonSpanExporterTest {
  /**
   * A batch already in flight when the exporter shuts down still reaches [JaegerJsonSpanExporter.export].
   *
   * It used to write into the closed generator, whose output buffer had gone back to the recycler, and fail
   * with a `NullPointerException` out of `System.arraycopy`. `BatchSpanProcessor` logs that as an error, which
   * a test run reports as a failure of whatever test happened to be running.
   */
  @Test
  fun `export after shutdown drops the batch instead of writing to a closed file`(@TempDir directory: Path): Unit = runBlocking {
    val file = directory.resolve("trace.json")
    val exporter = JaegerJsonSpanExporter(file = file, serviceName = "test")
    exporter.shutdown()
    val sizeAfterShutdown = file.fileSize()

    exporter.export(listOf(TestSpanData))

    file.fileSize() shouldBe sizeAfterShutdown
  }

  @Test
  fun `export before shutdown writes the span`(@TempDir directory: Path): Unit = runBlocking {
    val file = directory.resolve("trace.json")
    val exporter = JaegerJsonSpanExporter(file = file, serviceName = "test")

    exporter.export(listOf(TestSpanData))
    exporter.shutdown()

    (file.fileSize() > 0) shouldBe true
  }
}

private object TestSpanData : SpanData {
  override fun getName(): String = "test-span"
  override fun getKind(): SpanKind = SpanKind.INTERNAL
  override fun getSpanContext(): SpanContext = SpanContext.getInvalid()
  override fun getParentSpanContext(): SpanContext = SpanContext.getInvalid()
  override fun getStatus(): StatusData = StatusData.unset()
  override fun getStartEpochNanos(): Long = 0
  override fun getAttributes(): Attributes = Attributes.empty()
  override fun getEvents(): List<EventData> = emptyList()
  override fun getLinks(): List<LinkData> = emptyList()
  override fun getEndEpochNanos(): Long = 1
  override fun hasEnded(): Boolean = true
  override fun getTotalRecordedEvents(): Int = 0
  override fun getTotalRecordedLinks(): Int = 0
  override fun getTotalAttributeCount(): Int = 0
  override fun getInstrumentationScopeInfo(): InstrumentationScopeInfo = InstrumentationScopeInfo.empty()
  override fun getResource(): Resource = Resource.empty()

  @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
  override fun getInstrumentationLibraryInfo(): InstrumentationLibraryInfo = InstrumentationLibraryInfo.empty()
}
