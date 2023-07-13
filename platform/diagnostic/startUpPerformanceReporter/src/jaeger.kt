// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package com.intellij.platform.diagnostic.startUpPerformanceReporter

import com.intellij.diagnostic.ActivityImpl
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.util.io.Ksuid
import com.intellij.util.io.bytesToHex
import com.intellij.util.io.jackson.array
import com.intellij.util.io.jackson.obj
import java.io.BufferedWriter
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlin.random.Random

internal fun writeInJaegerJsonFormat(unsortedActivities: List<ActivityImpl>, output: BufferedWriter) {
  val activities = unsortedActivities.sortedWith(itemComparator)

  val absoluteStartTime = TimeUnit.MILLISECONDS.toMicros(StartUpMeasurer.getStartTimeUnixMillis())
  val javaStartTimeNano = StartUpMeasurer.getStartTime()
  val w = createJsonGenerator(output)
  w.use {
    w.obj {
      w.array("data") {
        w.obj {
          w.writeStringField("traceID", Ksuid.generate())
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
          val parentToId = HashMap<ActivityImpl, String>(activities.size)

          val random = Random

          w.array("spans") {
            val threadNameManager = IdeThreadNameManager()
            for (activity in activities) {
              w.obj {
                val spanId = bytesToHex(random.nextBytes(8))
                check(parentToId.put(activity, spanId) == null)

                val traceId: String
                if (activity.parent == null) {
                  traceId = bytesToHex(random.nextBytes(16))
                }
                else {
                  traceId = activityToTraceId.get(activity.parent)!!
                }
                activityToTraceId.put(activity, traceId)

                w.writeStringField("traceID", traceId)
                w.writeStringField("spanID", spanId)
                w.writeStringField("operationName", activity.name)
                w.writeStringField("processID", "p1")
                w.writeNumberField("startTime", absoluteStartTime + TimeUnit.NANOSECONDS.toMicros(activity.start - javaStartTimeNano))
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
        }
      }
    }
  }
}