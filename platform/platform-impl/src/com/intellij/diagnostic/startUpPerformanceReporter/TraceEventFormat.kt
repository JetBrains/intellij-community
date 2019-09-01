// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic.startUpPerformanceReporter

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonGenerator
import com.intellij.diagnostic.ActivityImpl
import com.intellij.util.containers.ObjectIntHashMap
import com.intellij.util.io.jackson.array
import com.intellij.util.io.jackson.obj
import java.io.OutputStreamWriter
import java.util.concurrent.TimeUnit

// https://docs.google.com/document/d/1CvAClvFfyA5R-PhYUmn5OOQtYMH4h6I0nSsKchNAySU/edit#
// ph - phase
// dur - duration
// pid - process id
// tid - thread id
class TraceEventFormat(private val timeOffset: Long) {
  private val threadNameToId = ObjectIntHashMap<String>()
  private var threadIdCounter = 0

  fun write(items: List<ActivityImpl>, outputWriter: OutputStreamWriter) {
    val writer = JsonFactory().createGenerator(outputWriter)
    writer.prettyPrinter = MyJsonPrettyPrinter()
    writer.use {
      writer.obj {
        writer.array("traceEvents") {
          for (item in items) {
            writer.obj {
              writeTraceEvent(item, writer)
            }
          }
        }
      }
    }
  }

  private fun writeTraceEvent(item: ActivityImpl, writer: JsonGenerator) {
    writer.writeStringField("name", item.name)
    writer.writeStringField("ph", "X")
    writer.writeNumberField("ts", TimeUnit.NANOSECONDS.toMicros(item.start - timeOffset))
    writer.writeNumberField("dur", TimeUnit.NANOSECONDS.toMicros(item.end - item.start))
    writer.writeNumberField("pid", 1)
    writer.writeNumberField("tid", getThreadId(item))
    if (item.description != null) {
      writer.obj("args") {
        writer.writeStringField("description", item.description)
      }
    }
  }

  private fun getThreadId(item: ActivityImpl): Int {
    val key = normalizeThreadName(item.thread)
    var result = threadNameToId.get(key)
    if (result == -1) {
      result = threadIdCounter++
      threadNameToId.put(key, result)
    }
    return result
  }
}