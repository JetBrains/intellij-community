// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.telemetry

import com.intellij.platform.diagnostic.telemetry.exporters.BatchSpanProcessor
import com.intellij.platform.diagnostic.telemetry.exporters.JaegerJsonSpanExporter
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

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
}