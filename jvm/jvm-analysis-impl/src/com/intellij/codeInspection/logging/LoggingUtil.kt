// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.logging

import com.siyeh.ig.callMatcher.CallMatcher
import org.jetbrains.uast.*

internal class LoggingUtil {
  companion object {
    private const val SLF4J_LOGGER = "org.slf4j.Logger"

    private const val LOG4J_LOGGER = "org.apache.logging.log4j.Logger"

    private const val LOG4J_LOG_BUILDER = "org.apache.logging.log4j.LogBuilder"

    private const val SLF4J_EVENT_BUILDER = "org.slf4j.spi.LoggingEventBuilder"

    private val SLF4J_MATCHER: CallMatcher.Simple = CallMatcher.instanceCall(SLF4J_LOGGER, "trace", "debug", "info", "warn", "error")
    private val LOG4J_MATCHER: CallMatcher.Simple = CallMatcher.instanceCall(LOG4J_LOGGER, "trace", "debug", "info", "warn",
                                                                             "error", "fatal", "log")
    private val LOG4J_BUILDER_MATCHER: CallMatcher.Simple = CallMatcher.instanceCall(LOG4J_LOG_BUILDER, "log")
    private val SLF4J_BUILDER_MATCHER: CallMatcher.Simple = CallMatcher.instanceCall(SLF4J_EVENT_BUILDER, "log")
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

    fun isGuarded(call: UCallExpression): Boolean {
      val variable: UVariable = getLoggerQualifier(call) ?: return false
      val loggerLevel = getLoggerLevel(call) ?: return false
      val ifExpression: UIfExpression? = call.getParentOfType<UIfExpression>()
      val condition = ifExpression?.condition ?: return false
      return isGuardedIn(condition, variable, loggerLevel)
    }

    private fun isGuardedIn(condition: UExpression, variable: UVariable, loggerLevel: LevelType): Boolean {
      val loggerLevelFromCondition: LevelType = getLevelFromCondition(condition, variable) ?: return false
      return loggerLevelFromCondition == loggerLevel
    }

    private fun getLevelFromCondition(condition: UExpression, variable: UVariable): LevelType? {
      if (condition is UCallExpression) {
        if ((condition.receiver as? UResolvable)?.resolveToUElement()?.sourcePsi != variable.sourcePsi) {
          return null
        }
        val methodName = condition.methodName ?: return null
        return levelTypeFromGuard(methodName)
      }
      if (condition is UQualifiedReferenceExpression) {
        if ((condition.receiver as? UResolvable)?.resolveToUElement()?.sourcePsi != variable.sourcePsi) {
          return null
        }
        val methodName = condition.resolvedName ?: return null
        return levelTypeFromGuard(methodName)
      }
      if (condition is UPolyadicExpression) {
        for (operand in condition.operands) {
          val levelFromCondition = getLevelFromCondition(operand, variable)
          if (levelFromCondition != null) return levelFromCondition
        }
      }
      return null
    }

    private fun levelTypeFromGuard(methodName: String): LevelType? {
      if (!methodName.startsWith("is") || !methodName.endsWith("Enabled")) {
        return null
      }
      for (level in LevelType.values()) {
        if (methodName.substring(2, methodName.length - 7).equals(level.name, ignoreCase = true)) {
          return level
        }
      }
      return null
    }

    private fun getLoggerQualifier(call: UCallExpression?): UVariable? {
      if (call == null) return null
      var receiver: UExpression? = call.receiver
      if (receiver is UCallExpression) {
        receiver = receiver.receiver
      }
      if (receiver is UQualifiedReferenceExpression) {
        receiver = receiver.receiver
      }
      if (receiver is USimpleNameReferenceExpression) {
        val resolved = receiver.resolveToUElement() as? UVariable ?: return null
        if (resolved.type.equalsToText(SLF4J_LOGGER) ||
            resolved.type.equalsToText(LOG4J_LOGGER)) {
          return resolved
        }
        if (resolved.type.equalsToText(SLF4J_EVENT_BUILDER) ||
            resolved.type.equalsToText(LOG4J_LOG_BUILDER)) {
          val uastInitializer = (resolved.uastInitializer as? UQualifiedReferenceExpression) ?: return null
          return getLoggerQualifier(uastInitializer.selector as? UCallExpression)
        }
      }
      return null
    }

    fun getLoggerLevel(uCall: UCallExpression?): LevelType? {
      if (uCall == null) {
        return null
      }

      var levelName = uCall.methodName
      if ("log" == levelName) {
        //also, it could be LOG4J_LOGGER, for example, log(level, pattern),
        //but let's skip it, because it is usually used for dynamic choice
        var receiver: UElement = uCall.receiver ?: return null
        if (receiver is UQualifiedReferenceExpression) {
          receiver = receiver.selector
        }
        else if (receiver is USimpleNameReferenceExpression) {
          val variable = receiver.resolveToUElement() as? UVariable ?: return null
          receiver = (variable.uastInitializer as? UQualifiedReferenceExpression)?.selector ?: return null
        }
        levelName = (receiver as? UCallExpression)?.methodName
      }
      if (levelName == null) {
        return null
      }
      for (value in LevelType.values()) {
        if (value.name.equals(levelName, ignoreCase = true) ||
            "at${value.name}".equals(levelName, ignoreCase = true)
        ) {
          return value
        }
      }
      return null
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

    enum class LevelType {
      FATAL, ERROR, WARNING, INFO, DEBUG, TRACE
    }
  }
}