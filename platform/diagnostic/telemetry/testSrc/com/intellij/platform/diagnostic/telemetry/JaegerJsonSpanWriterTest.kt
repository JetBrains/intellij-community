// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.telemetry

import com.intellij.platform.diagnostic.telemetry.exporters.JaegerJsonSpanWriter
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
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import tools.jackson.core.JsonToken
import tools.jackson.core.ObjectReadContext
import tools.jackson.core.json.JsonFactory
import java.nio.file.Path
import kotlin.io.path.fileSize

class JaegerJsonSpanWriterTest {
  @Test
  fun `write, flush, write, close produces a parseable file with both spans`(@TempDir directory: Path) {
    val file = directory.resolve("trace.json")
    val writer = JaegerJsonSpanWriter(file = file, serviceName = "test")

    writer.write(listOf(TestSpan("first")))
    writer.flush()
    operationNames(file) shouldBe listOf("first")

    writer.write(listOf(TestSpan("second")))
    writer.close()

    operationNames(file) shouldBe listOf("first", "second")
    writer.isClosed shouldBe true
  }

  @Test
  fun `flush on a fresh writer leaves a parseable file`(@TempDir directory: Path) {
    val file = directory.resolve("trace.json")
    JaegerJsonSpanWriter(file = file, serviceName = "test").use { writer ->
      writer.flush()

      operationNames(file) shouldBe emptyList()
    }
  }

  @Test
  fun `write after close does not change the file`(@TempDir directory: Path) {
    val file = directory.resolve("trace.json")
    val writer = JaegerJsonSpanWriter(file = file, serviceName = "test")
    writer.close()
    val sizeAfterClose = file.fileSize()

    writer.write(listOf(TestSpan("dropped")))
    writer.flush()
    writer.close()

    file.fileSize() shouldBe sizeAfterClose
    operationNames(file) shouldBe emptyList()
  }
}

/** Parses the whole file and returns the `operationName` of every span. A truncated file fails the parse. */
private fun operationNames(file: Path): List<String> {
  val names = ArrayList<String>()
  JsonFactory().createParser(ObjectReadContext.empty(), file).use { parser ->
    while (true) {
      val token = parser.nextToken() ?: break
      if (token == JsonToken.PROPERTY_NAME && parser.currentName() == "operationName") {
        parser.nextToken()
        names.add(parser.string)
      }
    }
  }
  return names
}

private class TestSpan(private val name: String) : SpanData {
  override fun getName(): String = name
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
