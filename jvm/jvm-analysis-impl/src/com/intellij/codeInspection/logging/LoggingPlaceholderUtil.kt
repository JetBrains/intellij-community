// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.logging

import com.intellij.psi.PsiVariable
import com.siyeh.ig.callMatcher.CallMapper
import com.siyeh.ig.callMatcher.CallMatcher
import com.siyeh.ig.psiutils.TypeUtils
import org.jetbrains.uast.*

internal const val MAX_BUILDER_LENGTH = 20

internal fun getLogStringIndex(parameters: List<UParameter>): Int? {
  if (parameters.isEmpty()) return null
  if (!TypeUtils.isJavaLangString(parameters[0].type)) {
    if (parameters.size < 2 || !TypeUtils.isJavaLangString(parameters[1].type)) {
      return null
    }
    else {
      return 2
    }
  }
  else {
    return 1
  }
}

private val SLF4J_HOLDER = object : LoggerTypeSearcher {
  override fun findType(expression: UCallExpression, context: LoggerContext): PlaceholderLoggerType {
    return if (context.log4jAsImplementationForSlf4j) { //use old style as more common
      PlaceholderLoggerType.LOG4J_OLD_STYLE
    }
    else PlaceholderLoggerType.SLF4J
  }
}

private val LOG4J_LOG_BUILDER_HOLDER = object : LoggerTypeSearcher {
  override fun findType(expression: UCallExpression, context: LoggerContext): PlaceholderLoggerType? {
    var qualifierExpression = getImmediateLoggerQualifier(expression)
    if (qualifierExpression is UReferenceExpression) {
      val target: UVariable = qualifierExpression.resolveToUElement() as? UVariable ?: return null
      if (!target.isFinal) {
        return PlaceholderLoggerType.LOG4J_EQUAL_PLACEHOLDERS //formatted builder is really rare
      }
      qualifierExpression = target.uastInitializer?.skipParenthesizedExprDown()
    }
    if (qualifierExpression is UQualifiedReferenceExpression) {
      qualifierExpression = qualifierExpression.selector
    }
    if (qualifierExpression is UCallExpression) {
      return when (LOG4J_HOLDER.findType(qualifierExpression, context)) {
        PlaceholderLoggerType.LOG4J_FORMATTED_STYLE -> PlaceholderLoggerType.LOG4J_FORMATTED_STYLE
        PlaceholderLoggerType.LOG4J_OLD_STYLE -> PlaceholderLoggerType.LOG4J_EQUAL_PLACEHOLDERS
        else -> null
      }
    }
    return PlaceholderLoggerType.LOG4J_EQUAL_PLACEHOLDERS
  }
}


internal val SLF4J_BUILDER_HOLDER = object : LoggerTypeSearcher {
  override fun findType(expression: UCallExpression, context: LoggerContext): PlaceholderLoggerType {
    if (context.log4jAsImplementationForSlf4j) {
      return PlaceholderLoggerType.SLF4J_EQUAL_PLACEHOLDERS
    }
    return PlaceholderLoggerType.SLF4J
  }
}

private val LOG4J_HOLDER = object : LoggerTypeSearcher {
  override fun findType(expression: UCallExpression, context: LoggerContext): PlaceholderLoggerType? {
    val qualifierExpression = getImmediateLoggerQualifier(expression)
    var initializer: UExpression? = null
    if (qualifierExpression is UReferenceExpression) {
      var resolvedToUElement = qualifierExpression.resolveToUElement()
      if (resolvedToUElement is UMethod) {
        //convert kotlin light method of property's accessor to UField
        resolvedToUElement = resolvedToUElement.sourcePsi.toUElement()
      }
      val target: UVariable = resolvedToUElement as? UVariable ?: return null
      val sourcePsi = target.sourcePsi as? PsiVariable
      // for lombok or other annotation generators. LOG4J_OLD_STYLE is the most common decision for that
      if (sourcePsi != null && !sourcePsi.isPhysical) {
        return PlaceholderLoggerType.LOG4J_OLD_STYLE
      }
      if (!target.isFinal) {
        return null
      }
      initializer = target.uastInitializer
      if (initializer == null) return null
    }
    else if (qualifierExpression is UCallExpression) {
      initializer = qualifierExpression
    }
    initializer = initializer?.skipParenthesizedExprDown()
    if (initializer is UQualifiedReferenceExpression) {
      initializer = initializer.selector
    }
    return if (initializer is UCallExpression && LoggingUtil.FORMATTED_LOG4J.uCallMatches(initializer)) {
      PlaceholderLoggerType.LOG4J_FORMATTED_STYLE
    }
    else PlaceholderLoggerType.LOG4J_OLD_STYLE
  }
}

private val AKKA_PLACEHOLDERS = object : LoggerTypeSearcher {
  override fun findType(expression: UCallExpression, context: LoggerContext): PlaceholderLoggerType {
    return PlaceholderLoggerType.AKKA_PLACEHOLDERS
  }
}

private val IDEA_PLACEHOLDERS = object : LoggerTypeSearcher {
  override fun findType(expression: UCallExpression, context: LoggerContext): PlaceholderLoggerType {
    return PlaceholderLoggerType.LOG4J_OLD_STYLE
  }
}

internal class LoggerContext(val log4jAsImplementationForSlf4j: Boolean)

internal interface LoggerTypeSearcher {
  fun findType(expression: UCallExpression, context: LoggerContext): PlaceholderLoggerType?
}

private fun getImmediateLoggerQualifier(expression: UCallExpression): UExpression? {
  val result = expression.receiver?.skipParenthesizedExprDown()
  if (result is UQualifiedReferenceExpression) {
    return result.selector
  }
  return result
}

internal val LOGGER_TYPE_SEARCHERS: CallMapper<LoggerTypeSearcher> = CallMapper<LoggerTypeSearcher>()
  .register(CallMatcher.instanceCall(LoggingUtil.SLF4J_LOGGER, "trace", "debug", "info", "warn", "error"), SLF4J_HOLDER)
  .register(CallMatcher.instanceCall(LoggingUtil.IDEA_LOGGER, "trace", "debug", "info", "warn", "error"), IDEA_PLACEHOLDERS)
  .register(CallMatcher.instanceCall(LoggingUtil.SLF4J_EVENT_BUILDER, "log"), SLF4J_BUILDER_HOLDER)
  .register(CallMatcher.instanceCall(LoggingUtil.LOG4J_LOGGER, "trace", "debug", "info", "warn", "error", "fatal", "log"), LOG4J_HOLDER)
  .register(CallMatcher.instanceCall(LoggingUtil.LOG4J_LOG_BUILDER, "log"), LOG4J_LOG_BUILDER_HOLDER)
  .register(CallMatcher.instanceCall(LoggingUtil.AKKA_LOGGING, "debug", "error", "format", "info", "log", "warning"), AKKA_PLACEHOLDERS)


private val BUILDER_CHAIN = setOf("addKeyValue", "addMarker", "setCause")
private const val ADD_ARGUMENT = "addArgument"
private const val SET_MESSAGE = "setMessage"

internal fun findMessageSetterStringArg(node: UCallExpression,
                                        loggerType: LoggerTypeSearcher?): UExpression? {
  if (loggerType == null) {
    return null
  }
  if (loggerType != SLF4J_BUILDER_HOLDER) {
    return null
  }
  var currentCall = node.receiver
  for (ignore in 0..MAX_BUILDER_LENGTH) {
    if (currentCall is UQualifiedReferenceExpression) {
      currentCall = currentCall.selector
      continue
    }
    if (currentCall !is UCallExpression) {
      return null
    }
    val methodName = currentCall.methodName ?: return null
    if (methodName == SET_MESSAGE && currentCall.valueArgumentCount == 1) {
      val uExpression = currentCall.valueArguments[0]
      if (!TypeUtils.isJavaLangString(uExpression.getExpressionType())) {
        return null
      }
      return uExpression
    }
    if (BUILDER_CHAIN.contains(methodName) || methodName == ADD_ARGUMENT) {
      currentCall = currentCall.receiver
      continue
    }
    return null
  }
  return null
}

/**
 * @return The count of additional arguments, or null if it is impossible to count.
 */
internal fun findAdditionalArgumentCount(node: UCallExpression,
                                         loggerType: LoggerTypeSearcher,
                                         allowIntermediateMessage: Boolean): Int? {
  if (loggerType != SLF4J_BUILDER_HOLDER) {
    return 0
  }
  var additionalArgumentCount = 0
  var currentCall = node.receiver
  for (ignore in 0..MAX_BUILDER_LENGTH) {
    if (currentCall is UQualifiedReferenceExpression) {
      currentCall = currentCall.selector
    }
    if (currentCall is UCallExpression) {
      val methodName = currentCall.methodName ?: return null
      if (methodName == ADD_ARGUMENT) {
        additionalArgumentCount++
        currentCall = currentCall.receiver
        continue
      }
      if (methodName.startsWith("at") && LoggingUtil.getLoggerLevel(currentCall) != null) {
        return additionalArgumentCount
      }
      if (BUILDER_CHAIN.contains(methodName) || (allowIntermediateMessage && methodName == SET_MESSAGE)) {
        currentCall = currentCall.receiver
        continue
      }
      return null
    }
    return null
  }
  return null
}

internal enum class PlaceholderLoggerType {
  SLF4J, SLF4J_EQUAL_PLACEHOLDERS, LOG4J_OLD_STYLE, LOG4J_FORMATTED_STYLE, LOG4J_EQUAL_PLACEHOLDERS, AKKA_PLACEHOLDERS
}

internal enum class PlaceholdersStatus {
  EXACTLY, PARTIAL, ERROR_TO_PARSE_STRING, EMPTY
}
