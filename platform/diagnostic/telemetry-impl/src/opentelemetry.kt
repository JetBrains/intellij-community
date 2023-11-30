// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.platform.diagnostic.telemetry.impl

import com.intellij.diagnostic.ActivityImpl
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.platform.util.http.ContentType
import com.intellij.platform.util.http.httpPost
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoIntegerType
import kotlinx.serialization.protobuf.ProtoPacked
import kotlinx.serialization.protobuf.ProtoType
import java.nio.ByteBuffer
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.ThreadLocalRandom

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

@Serializable
internal class AnyValue(
  @JvmField val string: String? = null,
)

@Serializable
internal class KeyValue(
  @JvmField val key: String,
  @JvmField val value: AnyValue,
)

@Suppress("unused")
@Serializable
internal class Resource(
  @ProtoPacked
  @JvmField val attributes: List<KeyValue> = emptyList(),
  @JvmField val droppedAttributesCount: Int = 0,
)

// https://kotlinlang.org/api/kotlinx.serialization/kotlinx-serialization-protobuf/kotlinx.serialization.protobuf/-proto-buf/

// https://github.com/open-telemetry/opentelemetry-proto/blob/v1.0.0/opentelemetry/proto/trace/v1/trace.proto
@Serializable
internal class TracesData(
  @ProtoPacked
  @JvmField val resourceSpans: List<ResourceSpans> = emptyList()
)

@Serializable
internal class ResourceSpans(
  @JvmField val resource: Resource? = null,
  @ProtoPacked
  @JvmField val scopeSpans: List<ScopeSpans> = emptyList(),
  @JvmField val schemaUrl: String? = null,
)

@Serializable
internal class ScopeSpans(
  @JvmField val scope: InstrumentationScope? = null,
  @ProtoPacked
  @JvmField val spans: List<Span> = emptyList(),
  @JvmField val schemaUrl: String? = null,
)

@Serializable
internal class Span(
  @JvmField val traceId: ByteArray,
  @JvmField val spanId: ByteArray,
  @JvmField val traceState: String? = null,
  @JvmField val parentSpanId: ByteArray? = null,
  @JvmField val name: String,
  @JvmField val kind: SpanKind = SpanKind.SPAN_KIND_INTERNAL,

  @ProtoType(ProtoIntegerType.FIXED)
  @JvmField val startTimeUnixNano: Long,
  @ProtoType(ProtoIntegerType.FIXED)
  @JvmField val endTimeUnixNano: Long,

  @ProtoPacked
  @JvmField val attributes: List<KeyValue> = emptyList(),
)

@Suppress("unused")
@Serializable
internal enum class SpanKind {
  SPAN_KIND_UNSPECIFIED, SPAN_KIND_INTERNAL, SPAN_KIND_SERVER, SPAN_KIND_CLIENT, SPAN_KIND_PRODUCER, SPAN_KIND_CONSUMER
}

@Serializable
internal class InstrumentationScope(
  @JvmField val name: String = "",
  @JvmField val version: String,
  @ProtoPacked
  @JvmField val attributes: List<KeyValue> = emptyList(),
)

// https://github.com/segmentio/ksuid/blob/b65a0ff7071caf0c8770b63babb7ae4a3c31034d/ksuid.go#L19
private fun generateTraceId(random: Random): ByteArray {
  return generateCustom(12, random).array()
}

internal fun generateSpanId(random: Random): ByteArray {
  val result = ByteArray(8)
  random.nextBytes(result)
  return result
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