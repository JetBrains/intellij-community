// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.util.ExceptionUtil
import java.util.logging.Formatter
import java.util.logging.LogRecord
import java.util.logging.StreamHandler

class JsonLogHandler : StreamHandler() {
  private val objectMapper by lazy { ObjectMapper() }
  private val formatter = object : Formatter() {
    override fun format(record: LogRecord): String {
      return formatMessage(record)
    }
  }

  override fun publish(record: LogRecord) {
    println(objectMapper.writeValueAsString(mapOf(
      "time" to record.millis,
      "threadID" to record.threadID,
      "loggerName" to record.loggerName,
      "level" to record.level.name,
      "message" to if (record.thrown != null) ExceptionUtil.getThrowableText(record.thrown) else formatter.format(record)
    )))
  }
}