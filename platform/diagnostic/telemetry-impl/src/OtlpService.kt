// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.platform.diagnostic.telemetry.impl

import com.intellij.diagnostic.ActivityImpl
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.platform.diagnostic.telemetry.Scope
import com.intellij.platform.diagnostic.telemetry.exporters.*
import com.intellij.platform.util.http.ContentType
import com.intellij.platform.util.http.httpPost
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import java.nio.ByteBuffer
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.ThreadLocalRandom
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

// https://github.com/segmentio/ksuid/blob/b65a0ff7071caf0c8770b63babb7ae4a3c31034d/ksuid.go#L19
private fun generateTraceId(random: Random): ByteArray {
  return generateCustom(12, random).array()
}

@Suppress("SameParameterValue")
private fun generateCustom(payloadLength: Int, random: Random): ByteBuffer {
  val byteBuffer = ByteBuffer.allocate(4 + payloadLength)
  val utc = ZonedDateTime.now(ZoneOffset.UTC).toInstant().toEpochMilli() / 1000
  val timestamp: Int = (utc - 1672531200).toInt()
  byteBuffer.putInt(timestamp)
  val bytes = ByteArray(payloadLength)
  random.nextBytes(bytes)
  byteBuffer.put(bytes)
  return byteBuffer
}

suspend fun writeInProtobufFormat(startTimeUnixNanoDiff: Long, activities: List<ActivityImpl>, endpoint: String) {
  if (activities.isEmpty()) {
    return
  }

  val random = ThreadLocalRandom.current()
  val rootSpan = Span(
    traceId = generateTraceId(random),
    name = "startup",
    startTimeUnixNano = startTimeUnixNanoDiff + activities.first().start,
    endTimeUnixNano = startTimeUnixNanoDiff + activities.last().end,
    spanId = generateSpanId(random),
  )

  val spans = ArrayList<Span>(activities.size + 1)
  spans.add(rootSpan)

  val activityToSpan = HashMap<ActivityImpl, Span>(activities.size)

  for (activity in activities) {
    val parentSpan = if (activity.parent == null) {
      rootSpan
    }
    else {
      activityToSpan.get(activity.parent)
    }

    val span = Span(
      traceId = rootSpan.traceId,
      spanId = generateSpanId(random),
      name = activity.name,
      startTimeUnixNano = startTimeUnixNanoDiff + activity.start,
      endTimeUnixNano = startTimeUnixNanoDiff + activity.end,
      parentSpanId = parentSpan?.spanId
    )

    check(activityToSpan.put(activity, span) == null)
    spans.add(span)
  }

  val appInfo = ApplicationInfo.getInstance()
  val data = createTraceData(resource = createOpenTelemetryResource(appInfo),
                             spans = spans,
                             instrumentationScope = InstrumentationScope(name = "startup",
                                                                         version = appInfo.build.asStringWithoutProductCode()))
  httpPost(url = endpoint, contentType = ContentType.XProtobuf, body = ProtoBuf.encodeToByteArray(data))
}

internal fun createTraceData(resource: Resource, spans: List<Span>, instrumentationScope: InstrumentationScope): TracesData {
  return TracesData(
    resourceSpans = listOf(
      ResourceSpans(
        resource = resource,
        scopeSpans = listOf(ScopeSpans(scope = instrumentationScope, spans = spans)),
      ),
    ),
  )
}

internal fun createOpenTelemetryResource(appInfo: ApplicationInfo): Resource {
  return Resource(attributes = listOf(
    KeyValue(key = "service.name",
             value = AnyValue(string = ApplicationNamesInfo.getInstance().fullProductName)),
    KeyValue(key = "service.version",
             value = AnyValue(string = appInfo.build.asStringWithoutProductCode())),
    KeyValue(key = "service.namespace", value = AnyValue(string = appInfo.build.productCode)),
    KeyValue(key = "service.instance.id",
             value = AnyValue(string = DateTimeFormatter.ISO_INSTANT.format(Instant.now()))),

    KeyValue(key = "process.owner",
             value = AnyValue(string = System.getProperty("user.name") ?: "unknown")),
    KeyValue(key = "os.type", value = AnyValue(string = SystemInfoRt.OS_NAME)),
    KeyValue(key = "os.version", value = AnyValue(string = SystemInfoRt.OS_VERSION)),
    KeyValue(key = "host.arch", value = AnyValue(string = System.getProperty("os.arch"))),
  ))
}

internal fun generateSpanId(random: Random): ByteArray {
  val result = ByteArray(8)
  random.nextBytes(result)
  return result
}