// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("PrivatePropertyName")

package com.intellij.diagnostic.telemetry

import com.intellij.openapi.diagnostic.Logger
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.content.OutputStreamContent
import io.opentelemetry.exporter.internal.otlp.traces.TraceRequestMarshaler
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SpanExporter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

private val httpClient by lazy {
  // HttpTimeout is not used - CIO engine handles that
  HttpClient(CIO) {
    expectSuccess = true

    install(HttpRequestRetry) {
      retryOnExceptionOrServerErrors(maxRetries = 3)
      exponentialDelay()
    }
  }
}

// ktor type for protobuf uses "protobuf", but go otlp requires "x-" prefix
private val Protobuf = ContentType("application", "x-protobuf")

internal class OtlpSpanExporter(endpoint: String, mainScope: CoroutineScope) : SpanExporter {
  private val unpublishedSpans = MutableSharedFlow<TraceRequestMarshaler>(extraBufferCapacity = 2_048)

  private val traceUrl = "${endpoint.removeSuffix("/")}/v1/traces"

  init {
    mainScope.launch(Dispatchers.IO) {
      unpublishedSpans
        .collect {
          export(item = it, url = traceUrl)
        }
    }
  }

  override fun export(spans: Collection<SpanData>): CompletableResultCode {
    val item = TraceRequestMarshaler.create(spans)
    check(unpublishedSpans.tryEmit(item)) {
      "Too many unprocessed items"
    }
    return CompletableResultCode.ofSuccess()
  }

  override fun flush(): CompletableResultCode {
    return CompletableResultCode.ofSuccess()
  }

  override fun shutdown(): CompletableResultCode {
    return CompletableResultCode.ofSuccess()
  }
}

private suspend fun export(item: TraceRequestMarshaler, url: String): CompletableResultCode {
  try {
    httpClient.post(url) {
      setBody(OutputStreamContent(contentType = Protobuf, contentLength = item.binarySerializedSize.toLong(), body = {
        item.writeBinaryTo(this)
      }))
    }
    return CompletableResultCode.ofSuccess()
  }
  catch (e: CancellationException) {
    throw e
  }
  catch (e: Throwable) {
    Logger.getInstance(OtlpSpanExporter::class.java).error("Failed to export opentelemetry spans (url=$url)", e)
    return CompletableResultCode.ofFailure()
  }
}
