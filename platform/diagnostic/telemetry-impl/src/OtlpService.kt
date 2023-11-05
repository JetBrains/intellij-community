// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.telemetry.impl

import com.intellij.diagnostic.ActivityImpl
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.platform.diagnostic.telemetry.Scope
import com.intellij.platform.diagnostic.telemetry.exporters.normalizeOtlpEndPoint
import com.intellij.platform.util.http.ContentType
import com.intellij.platform.util.http.httpPost
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import java.nio.ByteBuffer
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.time.Duration.Companion.minutes

private const val chunkSize = 512

fun getOtlpEndPoint(): String? {
  return normalizeOtlpEndPoint(System.getProperty("idea.diagnostic.opentelemetry.otlp"))
}

internal class OtlpService(private val coroutineScope: CoroutineScope, private val batchSpanProcessor: BatchSpanProcessor?) {
  private val spans = Channel<ActivityImpl>(capacity = Channel.UNLIMITED)

  private val utc = ((ZonedDateTime.now(ZoneOffset.UTC).toInstant().toEpochMilli() / 1000) - 1672531200).toInt()

  init {
    val endpoint = getOtlpEndPoint()
    if (endpoint != null) {
      process(endpoint)
    }
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  private fun process(endpoint: String) {
    coroutineScope.launch {
      val traceIdSalt = System.identityHashCode(spans).toLong() shl 32 or (System.identityHashCode(this).toLong() and 0xffffffffL)
      val startTimeUnixNanoDiff = StartUpMeasurer.getStartTimeUnixNanoDiff()

      val appInfo = ApplicationInfo.getInstance()
      val version = appInfo.build.asStringWithoutProductCode()

      val resource = createOpenTelemetryResource(appInfo)
      val scopeToSpans = HashMap<Scope, ScopeSpans>()
      try {
        var counter = 0
        while (true) {
          select {
            spans.onReceive { span ->
              val attributes = span.attributes
              val protoSpan = Span(
                traceId = computeTraceId(span, traceIdSalt),
                spanId = computeSpanId(span),
                name = span.name,
                startTimeUnixNano = startTimeUnixNanoDiff + span.start,
                endTimeUnixNano = startTimeUnixNanoDiff + span.end,
                parentSpanId = span.parent?.let { computeSpanId(it) },
                attributes = if (attributes == null) {
                  emptyList()
                }
                else {
                  val result = ArrayList<KeyValue>(attributes.size / 2)
                  for (i in attributes.indices step 2) {
                    result.add(KeyValue(attributes[i], AnyValue(string = attributes[i + 1])))
                  }
                  result
                },
              )

              (scopeToSpans.computeIfAbsent(span.scope!!) {
                ScopeSpans(scope = InstrumentationScope(name = it.toString(), version = version), spans = mutableListOf())
              }.spans as MutableList<Span>).add(protoSpan)

              if (counter++ >= chunkSize) {
                counter = 0
                try {
                  flush(scopeToSpans = scopeToSpans, resource = resource, endpoint = endpoint)
                }
                catch (e: CancellationException) {
                  throw e
                }
                catch (e: Throwable) {
                  thisLogger().error("Cannot flush", e)
                }
              }
            }

            // or if no new spans for a while, flush buffer
            onTimeout(5.minutes) {
              flush(scopeToSpans = scopeToSpans, resource = resource, endpoint = endpoint)
            }
          }
        }
      }
      catch (e: CancellationException) {
        if (!scopeToSpans.isEmpty()) {
          withContext(NonCancellable) {
            flush(scopeToSpans = scopeToSpans, resource = resource, endpoint = endpoint)
          }
        }
        throw e
      }
    }
  }

  private fun computeTraceId(span: ActivityImpl, traceIdSalt: Long): ByteArray {
    var rootSpan = span
    while (true) {
      val parentSpan = rootSpan.parent ?: break
      rootSpan = parentSpan
    }

    val byteBuffer = ByteBuffer.allocate(16)
    byteBuffer.putInt(utc)
    byteBuffer.putInt(System.identityHashCode(rootSpan))
    byteBuffer.putLong(traceIdSalt)
    return byteBuffer.array()
  }

  private fun computeSpanId(span: ActivityImpl): ByteArray {
    val byteBuffer = ByteBuffer.allocate(8)
    byteBuffer.putInt((span.start / 1000000).toInt())
    byteBuffer.putInt(System.identityHashCode(span))
    return byteBuffer.array()
  }

  private suspend fun flush(scopeToSpans: MutableMap<Scope, ScopeSpans>, resource: Resource, endpoint: String) {
    if (scopeToSpans.isEmpty()) {
      return
    }

    val scopeSpans = java.util.List.copyOf(scopeToSpans.values)
    batchSpanProcessor?.flushOtlp(scopeSpans)
    val data = TracesData(
      resourceSpans = listOf(
        ResourceSpans(
          resource = resource,
          scopeSpans = scopeSpans,
        ),
      ),
    )
    httpPost(url = endpoint, contentType = ContentType.XProtobuf, body = ProtoBuf.encodeToByteArray(data))
    scopeToSpans.clear()
  }

  fun add(activity: ActivityImpl) {
    spans.trySend(activity)
  }
}
