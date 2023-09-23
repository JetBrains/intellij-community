// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("PrivatePropertyName")

package com.intellij.platform.diagnostic.telemetry.exporters

import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.platform.diagnostic.telemetry.AsyncSpanExporter
import com.intellij.platform.diagnostic.telemetry.OpenTelemetryUtils
import com.intellij.platform.util.http.ContentType
import com.intellij.platform.util.http.httpPost
import io.opentelemetry.exporter.internal.otlp.traces.TraceRequestMarshaler
import io.opentelemetry.sdk.trace.data.SpanData
import kotlinx.coroutines.CancellationException
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class OtlpSpanExporter(private val traceUrl: String) : AsyncSpanExporter {
  override suspend fun export(spans: Collection<SpanData>) {
    // checking whether the spans are exported from rem dev backend
    if (System.getProperty(OpenTelemetryUtils.RDCT_TRACING_DIAGNOSTIC_FLAG) != null) {
      return
    }

    try {
      val item = TraceRequestMarshaler.create(spans)
      httpPost(traceUrl, contentLength = item.binarySerializedSize.toLong(), contentType = ContentType.XProtobuf) {
        item.writeBinaryTo(this)
      }
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: Throwable) {
      thisLogger().error("Failed to export opentelemetry spans (url=$traceUrl)", e)
    }
  }

  suspend fun exportBackendData(receivedBytes: ByteArray) {
    runCatching {
      httpPost(url = traceUrl, contentType = ContentType.XProtobuf, body = receivedBytes)
    }.getOrLogException(thisLogger())
  }
}