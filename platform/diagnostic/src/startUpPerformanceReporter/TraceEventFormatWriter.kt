// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic.startUpPerformanceReporter

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonGenerator
import com.intellij.diagnostic.ActivityImpl
import com.intellij.diagnostic.ThreadNameManager
import com.intellij.util.io.jackson.array
import com.intellij.util.io.jackson.obj
import java.io.OutputStreamWriter
import java.util.concurrent.TimeUnit

// https://docs.google.com/document/d/1CvAClvFfyA5R-PhYUmn5OOQtYMH4h6I0nSsKchNAySU/edit#
// ph - phase
// dur - duration
// pid - process id
// tid - thread id
internal class TraceEventFormatWriter(private val timeOffset: Long,
                                      private val instantEvents: List<ActivityImpl>,
                                      private val threadNameManager: ThreadNameManager) {
  fun writeInstantEvents(writer: JsonGenerator) {
    for (event in instantEvents) {
      writer.obj {
        writeCommonFields(event, writer)
        writer.writeStringField("ph", "i")
        writer.writeStringField("s", "g")
      }
    }
  }

  private fun writeServiceEvents(writer: JsonGenerator, unsortedServices: List<ActivityImpl>) {
    val servicesSortedByTime = unsortedServices.sortedWith(Comparator(::compareTime))
    val ownDurations = computeOwnTime(servicesSortedByTime, threadNameManager)

    for (event in servicesSortedByTime) {
      writer.obj {
        val computedOwnDuration = ownDurations.getLong(event)
        val duration = if (computedOwnDuration == -1L) event.end - event.start else computedOwnDuration
        writeCompleteEvent(event, writer, extraArgWriter = {
          writer.writeNumberField("ownDur", TimeUnit.NANOSECONDS.toMicros(duration))
        })
      }
    }
  }

  fun write(mainEvents: List<ActivityImpl>, categoryToActivity: Map<String, List<ActivityImpl>>, services: List<ActivityImpl>, outputWriter: OutputStreamWriter) {
    val writer = JsonFactory().createGenerator(outputWriter)
    writer.prettyPrinter = MyJsonPrettyPrinter()
    writer.use {
      writer.obj {
        writer.writeStringField("version", StartUpPerformanceReporter.VERSION)
        writer.array("traceEvents") {
          writeInstantEvents(writer)

          for (event in mainEvents) {
            writer.obj {
              writeCompleteEvent(event, writer)
            }
          }

          writeServiceEvents(writer, services)

          for (events in categoryToActivity.values) {
            for (event in events) {
              writer.obj {
                writeCompleteEvent(event, writer)
              }
            }
          }
        }
      }
    }
  }

  private fun writeCompleteEvent(event: ActivityImpl, writer: JsonGenerator, extraArgWriter: (() -> Unit)? = null) {
    writeCommonFields(event, writer)
    writer.writeStringField("ph", "X")
    writer.writeNumberField("dur", TimeUnit.NANOSECONDS.toMicros(event.end - event.start))
    if (event.description != null || event.pluginId != null || extraArgWriter != null) {
      writer.obj("args") {
        event.description?.let {
          writer.writeStringField("description", it)
        }
        event.pluginId?.let {
          writer.writeStringField("plugin", it)
        }

        extraArgWriter?.invoke()
      }
    }
  }

  private fun writeCommonFields(event: ActivityImpl, writer: JsonGenerator) {
    writer.writeStringField("name", event.name)
    writer.writeNumberField("ts", TimeUnit.NANOSECONDS.toMicros(event.start - timeOffset))
    writer.writeNumberField("pid", 1)
    writer.writeStringField("tid", threadNameManager.getThreadName(event))

    event.category?.let {
      writer.writeStringField("cat", it.jsonName)
    }
  }
}