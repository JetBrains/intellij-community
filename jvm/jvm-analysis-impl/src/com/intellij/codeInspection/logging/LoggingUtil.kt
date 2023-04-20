// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.logging

import com.siyeh.ig.callMatcher.CallMatcher
import org.jetbrains.uast.UCallExpression

const val SLF4J_LOGGER = "org.slf4j.Logger"

const val LOG4J_LOGGER = "org.apache.logging.log4j.Logger"

const val LOG4J_LOG_BUILDER = "org.apache.logging.log4j.LogBuilder"

const val SLF4J_EVENT_BUILDER = "org.slf4j.spi.LoggingEventBuilder"

val SLF4J_MATCHER: CallMatcher.Simple = CallMatcher.instanceCall(SLF4J_LOGGER, "trace", "debug", "info", "warn", "error")
val LOG4J_MATCHER: CallMatcher.Simple = CallMatcher.instanceCall(LOG4J_LOGGER, "trace", "debug", "info", "warn",
                                                                 "error", "fatal", "log")
val LOG4J_BUILDER_MATCHER: CallMatcher.Simple = CallMatcher.instanceCall(LOG4J_LOG_BUILDER, "log")
val SLF4J_BUILDER_MATCHER: CallMatcher.Simple = CallMatcher.instanceCall(SLF4J_EVENT_BUILDER, "log")
val LOG_MATCHERS: CallMatcher = CallMatcher.anyOf(
  SLF4J_MATCHER,
  LOG4J_MATCHER,
  LOG4J_BUILDER_MATCHER,
  SLF4J_BUILDER_MATCHER,
)

fun getLoggerType(uCall: UCallExpression?): LoggerType? {
  return if (SLF4J_MATCHER.uCallMatches(uCall)) {
    LoggerType.SLF4J_LOGGER_TYPE
  }
  else if (LOG4J_MATCHER.uCallMatches(uCall)) {
    LoggerType.LOG4J_LOGGER_TYPE
  }
  else if (LOG4J_BUILDER_MATCHER.uCallMatches(uCall)) {
    LoggerType.LOG4J_BUILDER_TYPE
  }
  else if (SLF4J_BUILDER_MATCHER.uCallMatches(uCall)) {
    LoggerType.SLF4J_BUILDER_TYPE
  }
  else {
    null
  }
}

fun countPlaceHolders(text: String, loggerType: LoggerType?): Int {
  var count = 0
  var placeHolder = false
  var escaped = false
  for (c in text) {
    if (c == '\\' && (loggerType == LoggerType.SLF4J_LOGGER_TYPE || loggerType == LoggerType.SLF4J_BUILDER_TYPE)) {
      escaped = !escaped
    }
    else if (c == '{') {
      if (!escaped) {
        placeHolder = true
      }
    }
    else if (c == '}') {
      if (placeHolder) {
        count++
      }
      placeHolder = false
      escaped = false
    }
    else {
      placeHolder = false
      escaped = false
    }
  }
  return count
}


enum class LoggerType {
  SLF4J_LOGGER_TYPE, SLF4J_BUILDER_TYPE, LOG4J_LOGGER_TYPE, LOG4J_BUILDER_TYPE
}