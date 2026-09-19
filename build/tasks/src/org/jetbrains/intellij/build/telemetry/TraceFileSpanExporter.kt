// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.telemetry

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.platform.diagnostic.telemetry.exporters.JaegerJsonSpanWriter
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SpanExporter
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

/**
 * Writes the Jaeger JSON trace file of the build through [JaegerJsonSpanWriter].
 *
 * Every call blocks on the calling thread. [flush] leaves the file as complete JSON, and [shutdown] closes it.
 */
@ApiStatus.Internal
class TraceFileSpanExporter(file: Path, serviceName: String) : SpanExporter {
  private val writer = JaegerJsonSpanWriter(file = file, serviceName = serviceName)

  override fun export(spans: Collection<SpanData>): CompletableResultCode = resultOf { writer.write(spans) }

  override fun flush(): CompletableResultCode = resultOf { writer.flush() }

  override fun shutdown(): CompletableResultCode = resultOf { writer.close() }
}

/** Runs [block] and reports a success. A thrown error becomes a failed result. */
internal inline fun resultOf(block: () -> Unit): CompletableResultCode {
  try {
    block()
    return CompletableResultCode.ofSuccess()
  }
  catch (e: Throwable) {
    rethrowControlFlowException(e)
    return CompletableResultCode.ofExceptionalFailure(e)
  }
}
