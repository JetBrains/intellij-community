// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplaceJavaStaticMethodWithKotlinAnalog")

package com.intellij.platform.diagnostic.telemetry.impl

import com.intellij.diagnostic.ActivityImpl
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.platform.diagnostic.telemetry.Scope
import com.intellij.platform.diagnostic.telemetry.exporters.*
import com.intellij.platform.util.http.ContentType
import com.intellij.platform.util.http.httpPost
import com.intellij.util.concurrency.SynchronizedClearableLazy
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import org.jetbrains.annotations.ApiStatus.Internal
import java.net.ConnectException
import java.nio.ByteBuffer
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.function.BiConsumer
import kotlin.time.Duration.Companion.minutes

private const val chunkSize = 512

internal class OtlpService private constructor() {
  private val spans = Channel<ActivityImpl?>(capacity = Channel.UNLIMITED)

  @JvmField
  internal val utc: Int = ((ZonedDateTime.now(ZoneOffset.UTC).toInstant().toEpochMilli() / 1000) - 1672531200).toInt()

  @JvmField
  internal val traceIdSalt: Long = System.identityHashCode(spans).toLong() shl 32 or (System.identityHashCode(this).toLong() and 0xffffffffL)

  @OptIn(ExperimentalCoroutinesApi::class)
  fun process(
    coroutineScope: CoroutineScope,
    batchSpanProcessor: BatchSpanProcessor?,
    endpoint: String?,
    opentelemetrySdkResource: io.opentelemetry.sdk.resources.Resource,
  ): Job? {
    if (endpoint == null && batchSpanProcessor == null) {
      return null
    }

    return coroutineScope.launch {
      val startTimeUnixNanoDiff = StartUpMeasurer.getStartTimeUnixNanoDiff()

      val appInfo = ApplicationInfoImpl.getShadowInstance()
      val version = appInfo.build.asStringWithoutProductCode()

      val resource = createOpenTelemetryResource(opentelemetrySdkResource)
      val scopeToSpans = HashMap<Scope, ScopeSpans>()
      try {
        var counter = 0
        while (true) {
          val finished = select {
            spans.onReceive { span ->
              if (span == null) {
                return@onReceive true
              }

              val attributes = span.attributes
              val protoSpan = Span(
                traceId = computeTraceId(span),
                spanId = computeSpanId(span),
                name = normalizeSpanName(span),
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
                flush(scopeToSpans = scopeToSpans, resource = resource, endpoint = endpoint, batchSpanProcessor = batchSpanProcessor)
              }

              false
            }

            // or if no new spans for a while, flush buffer
            onTimeout(5.minutes) {
              flush(scopeToSpans = scopeToSpans, resource = resource, endpoint = endpoint, batchSpanProcessor = batchSpanProcessor)
              false
            }
          }

          if (finished) {
            break
          }
        }

        // shutdown is requested
        if (!scopeToSpans.isEmpty()) {
          withContext(NonCancellable) {
            flush(scopeToSpans = scopeToSpans, resource = resource, endpoint = endpoint, batchSpanProcessor = batchSpanProcessor)
          }
        }
      }
      catch (e: CancellationException) {
        if (!scopeToSpans.isEmpty()) {
          withContext(NonCancellable) {
            flush(scopeToSpans = scopeToSpans, resource = resource, endpoint = endpoint, batchSpanProcessor = batchSpanProcessor)
          }
        }
        throw e
      }
    }
  }

  private suspend fun flush(scopeToSpans: MutableMap<Scope, ScopeSpans>,
                            resource: Resource,
                            endpoint: String?,
                            batchSpanProcessor: BatchSpanProcessor?) {
    if (scopeToSpans.isEmpty()) {
      return
    }

    try {
      batchSpanProcessor?.flushOtlp(scopeToSpans.values)

      if (endpoint != null) {
        val data = TracesData(
          resourceSpans = java.util.List.of(
            ResourceSpans(
              resource = resource,
              scopeSpans = java.util.List.copyOf(scopeToSpans.values),
            ),
          ),
        )
        httpPost(url = endpoint, contentType = ContentType.XProtobuf, body = ProtoBuf.encodeToByteArray(data))
      }
      scopeToSpans.clear()
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: ConnectException) {
      thisLogger().warn("Cannot flush: ${e.message}")
    }
    catch (e: Throwable) {
      thisLogger().error("Cannot flush: ${e.message}", e)
    }
  }

  fun add(activity: ActivityImpl) {
    spans.trySend(activity)
  }

  suspend fun stop() {
    spans.send(null)
    spans.close()
  }

  companion object {
    private val instance = SynchronizedClearableLazy(::OtlpService)

    fun getInstance(): OtlpService = instance.value
  }
}

@Internal
fun computeTraceId(span: ActivityImpl): ByteArray {
  val otlpService = OtlpService.getInstance()
  val traceIdSalt = otlpService.traceIdSalt
  val utc = otlpService.utc

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

@Internal
fun computeSpanId(span: ActivityImpl): ByteArray {
  val byteBuffer = ByteBuffer.allocate(8)
  byteBuffer.putInt((span.start / 1_000_000).toInt())
  byteBuffer.putInt(System.identityHashCode(span))
  return byteBuffer.array()
}

private fun createOpenTelemetryResource(opentelemetrySdkResource: io.opentelemetry.sdk.resources.Resource): Resource {
  val attributes = mutableListOf<KeyValue>()
  opentelemetrySdkResource.attributes.forEach(BiConsumer { k, v ->
    attributes.add(KeyValue(key = k.key, value = AnyValue(string = v.toString())))
  })
  return Resource(attributes = java.util.List.copyOf(attributes))
}

private val hashCodeRegex = Regex("@\\d+ ")

private fun normalizeSpanName(span: ActivityImpl): String {
  return span.name.replace(hashCodeRegex, " ")
}