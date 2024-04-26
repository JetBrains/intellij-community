// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.platform.diagnostic.telemetry.exporters

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoIntegerType
import kotlinx.serialization.protobuf.ProtoPacked
import kotlinx.serialization.protobuf.ProtoType

@Serializable
class AnyValue(
  @JvmField val string: String? = null,
)

@Serializable
class KeyValue(
  @JvmField val key: String,
  @JvmField val value: AnyValue,
)

@Suppress("unused")
@Serializable
class Resource(
  @ProtoPacked
  @JvmField val attributes: List<KeyValue> = emptyList(),
  @JvmField val droppedAttributesCount: Int = 0,
)

// https://kotlinlang.org/api/kotlinx.serialization/kotlinx-serialization-protobuf/kotlinx.serialization.protobuf/-proto-buf/

// https://github.com/open-telemetry/opentelemetry-proto/blob/v1.0.0/opentelemetry/proto/trace/v1/trace.proto
@Serializable
class TracesData(
  @ProtoPacked
  @JvmField val resourceSpans: List<ResourceSpans> = emptyList()
)

@Serializable
class ResourceSpans(
  @JvmField val resource: Resource? = null,
  @ProtoPacked
  @JvmField val scopeSpans: List<ScopeSpans> = emptyList(),
  @JvmField val schemaUrl: String? = null,
)

@Serializable
class ScopeSpans(
  @JvmField val scope: InstrumentationScope? = null,
  @ProtoPacked
  @JvmField val spans: List<Span> = emptyList(),
  @JvmField val schemaUrl: String? = null,
)

@Serializable
class Span(
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
enum class SpanKind {
  SPAN_KIND_UNSPECIFIED, SPAN_KIND_INTERNAL, SPAN_KIND_SERVER, SPAN_KIND_CLIENT, SPAN_KIND_PRODUCER, SPAN_KIND_CONSUMER
}

@Serializable
class InstrumentationScope(
  @JvmField val name: String = "",
  @JvmField val version: String,
  @ProtoPacked
  @JvmField val attributes: List<KeyValue> = emptyList(),
)