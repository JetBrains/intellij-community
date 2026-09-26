// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.telemetry.exporters

import com.intellij.platform.diagnostic.telemetry.AsyncSpanExporter
import io.opentelemetry.sdk.trace.data.SpanData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

/**
 * Writes spans to a Jaeger JSON trace file from a coroutine.
 *
 * The exporter delegates every call to a [JaegerJsonSpanWriter]. The file I/O of [flush], [flushOtlp], [reset], and
 * [shutdown] runs on [Dispatchers.IO]. [export] writes to the generator buffer on the caller thread.
 */
// https://github.com/jaegertracing/jaeger-ui/issues/381
@ApiStatus.Internal
class JaegerJsonSpanExporter(
  file: Path,
  val serviceName: String,
  val serviceVersion: String? = null,
  val serviceNamespace: String? = null,
) : AsyncSpanExporter {
  private val writer = JaegerJsonSpanWriter(
    file = file,
    serviceName = serviceName,
    serviceVersion = serviceVersion,
    serviceNamespace = serviceNamespace,
  )

  /** Drops the batch when the exporter is shut down. See [JaegerJsonSpanWriter.write]. */
  override suspend fun export(spans: Collection<SpanData>) {
    writer.write(spans)
  }

  /** Writes the OTLP spans and flushes the generator buffer to the file. */
  suspend fun flushOtlp(scopeSpans: Collection<ScopeSpans>) {
    withContext(Dispatchers.IO) {
      writer.writeOtlp(scopeSpans)
    }
  }

  override suspend fun shutdown() {
    withContext(Dispatchers.IO) {
      writer.close()
    }
  }

  override suspend fun flush() {
    withContext(Dispatchers.IO) {
      writer.flush()
    }
  }

  override suspend fun reset() {
    withContext(Dispatchers.IO) {
      writer.reset()
    }
  }
}
