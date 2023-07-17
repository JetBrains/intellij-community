// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package com.intellij.platform.diagnostic.startUpPerformanceReporter

import com.fasterxml.jackson.core.JsonGenerator
import com.intellij.diagnostic.ActivityImpl
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.platform.util.http.ContentType
import com.intellij.platform.util.http.post
import com.intellij.util.io.Ksuid
import com.intellij.util.io.bytesToHex
import com.intellij.util.io.jackson.array
import com.intellij.util.io.jackson.obj
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import java.io.BufferedWriter
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.TimeUnit

internal suspend fun sendStartupTraceUsingOtlp(unsortedActivities: List<ActivityImpl>, endpoint: String) {
  val absoluteStartTimeNano = TimeUnit.MILLISECONDS.toNanos(StartUpMeasurer.getStartTimeUnixMillis())
  val javaStartTimeNano = StartUpMeasurer.getStartTime()
  writeInProtobufFormat(absoluteStartTimeNano = absoluteStartTimeNano,
                        activities = unsortedActivities.sortedWith(itemComparator),
                        javaStartTimeNano = javaStartTimeNano,
                        endpoint = "${(if (endpoint == "true") "http://127.0.0.1:4318/" else endpoint).removeSuffix("/")}/v1/traces")
}

internal fun writeInJaegerJsonFormat(unsortedActivities: List<ActivityImpl>, output: BufferedWriter) {
  val activities = unsortedActivities.sortedWith(itemComparator)
  val javaStartTimeNano = StartUpMeasurer.getStartTime()

  val w = createJsonGenerator(output)
  w.use {
    w.obj {
      w.array("data") {
        w.obj {
          w.writeStringField("traceID", bytesToHex(generateTraceId(ThreadLocalRandom.current())))
          w.obj("processes") {
            w.obj("p1") {
              w.writeStringField("serviceName", "")
              w.array("tags") {
                w.obj {
                  w.writeStringField("key", "time")
                  w.writeStringField("type", "string")
                  w.writeStringField("value", ZonedDateTime.now().format(DateTimeFormatter.RFC_1123_DATE_TIME))
                }
              }
            }
          }

          val activityToTraceId = HashMap<ActivityImpl, String>()

          w.array("spans") {
            writeSpansInJaegerJson(activities = activities,
                                   w = w,
                                   activityToTraceId = activityToTraceId,
                                   javaStartTimeNano = javaStartTimeNano)
          }
        }
      }
    }
  }
}

// https://github.com/segmentio/ksuid/blob/b65a0ff7071caf0c8770b63babb7ae4a3c31034d/ksuid.go#L19
private fun generateTraceId(random: java.util.Random): ByteArray {
  return Ksuid.generateCustom(12, random).array()
}

private fun generateSpanId(random: java.util.Random): ByteArray {
  val result = ByteArray(8)
  random.nextBytes(result)
  return result
}

private suspend fun writeInProtobufFormat(absoluteStartTimeNano: Long,
                                          activities: List<ActivityImpl>,
                                          javaStartTimeNano: Long,
                                          endpoint: String) {
  if (activities.isEmpty()) {
    return
  }

  val random = ThreadLocalRandom.current()
  val rootSpan = Span(
    traceId = generateTraceId(random),
    name = "startup",
    startTimeUnixNano = absoluteStartTimeNano,
    endTimeUnixNano = absoluteStartTimeNano + (activities.last().end - javaStartTimeNano),
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
      activityToSpan.get(activity.parent)!!
    }

    val span = Span(
      traceId = rootSpan.traceId,
      spanId = generateSpanId(random),
      name = activity.name,
      startTimeUnixNano = absoluteStartTimeNano + (activity.start - javaStartTimeNano),
      endTimeUnixNano = absoluteStartTimeNano + (activity.end - javaStartTimeNano),
      parentSpanId = parentSpan.spanId
    )

    check(activityToSpan.put(activity, span) == null)
    spans.add(span)
  }

  val appInfo = ApplicationInfo.getInstance()
  val data = TracesData(
    resourceSpans = listOf(
      ResourceSpans(
        resource = Resource(attributes = listOf(
          KeyValue(key = "service.name", value = AnyValue(string = ApplicationNamesInfo.getInstance().fullProductName)),
          KeyValue(key = "service.version", value = AnyValue(string = appInfo.build.asStringWithoutProductCode())),
          KeyValue(key = "service.namespace", value = AnyValue(string = appInfo.build.productCode)),
          KeyValue(key = "service.instance.id", value = AnyValue(string = DateTimeFormatter.ISO_INSTANT.format(Instant.now()))),

          KeyValue(key = "process.owner", value = AnyValue(string = System.getProperty("user.name") ?: "unknown")),
          KeyValue(key = "os.type", value = AnyValue(string = SystemInfoRt.OS_NAME)),
          KeyValue(key = "os.version", value = AnyValue(string = SystemInfoRt.OS_VERSION)),
          KeyValue(key = "host.arch", value = AnyValue(string = System.getProperty("os.arch"))),
        )),
        scopeSpans = listOf(
          ScopeSpans(
            scope = InstrumentationScope(name = "com.intellij.platform.diagnostic.startUp",
                                         version = appInfo.build.asStringWithoutProductCode()),
            spans = spans,
          ),
        ),
      ),
    ),
  )

  post(url = endpoint, contentType = ContentType.XProtobuf, body = ProtoBuf.encodeToByteArray(data))
}

private fun writeSpansInJaegerJson(activities: List<ActivityImpl>,
                                   w: JsonGenerator,
                                   activityToTraceId: HashMap<ActivityImpl, String>,
                                   javaStartTimeNano: Long) {
  val absoluteStartTimeMicros = TimeUnit.MILLISECONDS.toMicros(StartUpMeasurer.getStartTimeUnixMillis())
  val parentToId = HashMap<ActivityImpl, String>(activities.size)
  val threadNameManager = IdeThreadNameManager()
  val random = ThreadLocalRandom.current()
  for (activity in activities) {
    w.obj {
      val spanId = bytesToHex(generateSpanId(random))
      check(parentToId.put(activity, spanId) == null)

      val traceId: String
      if (activity.parent == null) {
        traceId = bytesToHex(generateTraceId(random))
      }
      else {
        traceId = activityToTraceId.get(activity.parent)!!
      }
      activityToTraceId.put(activity, traceId)

      w.writeStringField("traceID", traceId)
      w.writeStringField("spanID", spanId)
      w.writeStringField("operationName", activity.name)
      w.writeStringField("processID", "p1")
      w.writeNumberField("startTime", absoluteStartTimeMicros + TimeUnit.NANOSECONDS.toMicros(activity.start - javaStartTimeNano))
      w.writeNumberField("duration", TimeUnit.NANOSECONDS.toMicros(activity.end - activity.start))

      w.array("tags") {
        w.obj {
          w.writeStringField("key", "thread.name")
          w.writeStringField("type", "string")
          w.writeStringField("value", threadNameManager.getThreadName(activity))
        }
        w.obj {
          w.writeStringField("key", "thread.id")
          w.writeStringField("type", "long")
          w.writeNumberField("value", activity.threadId)
        }

        activity.pluginId?.let {
          w.obj {
            w.writeStringField("key", "pluginId")
            w.writeStringField("type", "string")
            w.writeStringField("value", it)
          }
        }
      }

      if (activity.parent != null) {
        w.array("references") {
          w.obj {
            val parentId = requireNotNull(parentToId.get(activity.parent))
            w.writeStringField("refType", "CHILD_OF")
            w.writeStringField("traceID", traceId)
            w.writeStringField("spanID", parentId)
          }
        }
      }
    }
  }
}