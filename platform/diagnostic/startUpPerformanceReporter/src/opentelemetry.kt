// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.startUpPerformanceReporter

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoIntegerType
import kotlinx.serialization.protobuf.ProtoPacked
import kotlinx.serialization.protobuf.ProtoType

@Serializable
internal class AnyValue(
  val string: String? = null,
)

@Serializable
internal class KeyValue(
  val key: String,
  val value: AnyValue,
)

@Suppress("unused")
@Serializable
internal class Resource(
  @ProtoPacked
  val attributes: List<KeyValue> = emptyList(),
  val droppedAttributesCount: Int = 0,
)

// https://kotlinlang.org/api/kotlinx.serialization/kotlinx-serialization-protobuf/kotlinx.serialization.protobuf/-proto-buf/

// https://github.com/open-telemetry/opentelemetry-proto/blob/v1.0.0/opentelemetry/proto/trace/v1/trace.proto
@Serializable
internal class TracesData(
  @ProtoPacked
  val resourceSpans: List<ResourceSpans> = emptyList()
)

@Serializable
internal class ResourceSpans(
  val resource: Resource? = null,
  @ProtoPacked
  val scopeSpans: List<ScopeSpans> = emptyList(),
  val schemaUrl: String? = null,
)

@Serializable
internal class ScopeSpans(
  val scope: InstrumentationScope? = null,
  @ProtoPacked
  val spans: List<Span> = emptyList(),
  val schemaUrl: String? = null,
)

@Serializable
internal class Span(
  val traceId: ByteArray,
  val spanId: ByteArray,
  val traceState: String? = null,
  val parentSpanId: ByteArray? = null,
  val name: String,
  val kind: SpanKind = SpanKind.SPAN_KIND_INTERNAL,

  @ProtoType(ProtoIntegerType.FIXED)
  val startTimeUnixNano: Long,
  @ProtoType(ProtoIntegerType.FIXED)
  val endTimeUnixNano: Long
)

@Suppress("unused")
@Serializable
internal enum class SpanKind {
  SPAN_KIND_UNSPECIFIED, SPAN_KIND_INTERNAL, SPAN_KIND_SERVER, SPAN_KIND_CLIENT, SPAN_KIND_PRODUCER, SPAN_KIND_CONSUMER
}

@Serializable
internal class InstrumentationScope(
  val name: String = "",
  val version: String,
  @ProtoPacked
  val attributes: List<KeyValue> = emptyList(),
)