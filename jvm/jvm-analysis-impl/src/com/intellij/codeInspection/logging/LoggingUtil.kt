// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.logging

import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.PsiModificationTracker
import com.siyeh.ig.callMatcher.CallMatcher
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastVisitor

internal class LoggingUtil {
  companion object {
    internal const val SLF4J_LOGGER = "org.slf4j.Logger"

    internal const val LOG4J_LOGGER = "org.apache.logging.log4j.Logger"

    internal const val LOG4J_LOG_BUILDER = "org.apache.logging.log4j.LogBuilder"

    internal const val SLF4J_EVENT_BUILDER = "org.slf4j.spi.LoggingEventBuilder"

    private const val LEGACY_LOG4J_LOGGER = "org.apache.log4j.Logger"
    private const val LEGACY_CATEGORY_LOGGER = "org.apache.log4j.Category"
    private const val LEGACY_APACHE_COMMON_LOGGER = "org.apache.commons.logging.Log"
    private const val LEGACY_JAVA_LOGGER = "java.util.logging.Logger"

    internal const val AKKA_LOGGING = "akka.event.LoggingAdapter"

    internal const val IDEA_LOGGER = "com.intellij.openapi.diagnostic.Logger"

    private val LOGGER_CLASSES = setOf(SLF4J_LOGGER, LOG4J_LOGGER)
    private val LEGACY_LOGGER_CLASSES = setOf(LEGACY_LOG4J_LOGGER, LEGACY_CATEGORY_LOGGER,
                                              LEGACY_APACHE_COMMON_LOGGER, LEGACY_JAVA_LOGGER)

    private val SLF4J_MATCHER: CallMatcher.Simple = CallMatcher.instanceCall(SLF4J_LOGGER, "trace", "debug", "info", "warn", "error")
    private val LOG4J_MATCHER: CallMatcher.Simple = CallMatcher.instanceCall(LOG4J_LOGGER, "trace", "debug", "info", "warn",
                                                                             "error", "fatal", "log")
    private val LOG4J_BUILDER_MATCHER: CallMatcher.Simple = CallMatcher.instanceCall(LOG4J_LOG_BUILDER, "log")
    private val SLF4J_BUILDER_MATCHER: CallMatcher.Simple = CallMatcher.instanceCall(SLF4J_EVENT_BUILDER, "log")
    internal val LOG_MATCHERS: CallMatcher = CallMatcher.anyOf(
      SLF4J_MATCHER,
      LOG4J_MATCHER,
      LOG4J_BUILDER_MATCHER,
      SLF4J_BUILDER_MATCHER,
    )

    internal val FORMATTED_LOG4J: CallMatcher = CallMatcher.staticCall("org.apache.logging.log4j.LogManager", "getFormatterLogger")

    internal const val LOG_4_J_LOGGER = "org.apache.logging.slf4j.Log4jLogger"

    internal val LEGACY_LOG_MATCHERS: CallMatcher = CallMatcher.anyOf(
      CallMatcher.instanceCall(LEGACY_LOG4J_LOGGER, "trace", "debug", "info", "warn", "error", "fatal", "log", "l7dlog"),
      CallMatcher.instanceCall(LEGACY_CATEGORY_LOGGER, "debug", "info", "warn", "error", "fatal", "log", "l7dlog"),
      CallMatcher.instanceCall(LEGACY_APACHE_COMMON_LOGGER, "trace", "debug", "info", "warn", "error", "fatal"),
      CallMatcher.instanceCall(LEGACY_JAVA_LOGGER, "fine", "log", "finer", "finest", "logp", "logrb", "info", "severe", "warning", "config")
    )
    internal val IDEA_LOG_MATCHER: CallMatcher = CallMatcher.anyOf(
      CallMatcher.instanceCall(IDEA_LOGGER, "trace", "debug", "info", "warn", "error"),
    )

    private val LEGACY_METHODS_WITH_LEVEL = setOf("log", "l7dlog", "logp", "logrb")

    private val LEVEL_MAP: Map<String, LevelType> = LevelType.entries.associateBy { it.name }
    private val LEGACY_LEVEL_MAP: Map<String, LegacyLevelType> = LegacyLevelType.entries.associateBy { it.name }

    private val LEVEL_CLASSES = setOf("org.apache.logging.log4j.Level", "org.slf4j.event.Level")
    private val LEGACY_LEVEL_CLASSES = setOf("org.apache.logging.log4j.Level", "org.apache.log4j.Priority", "java.util.logging.Level")

    internal fun getLoggerType(uCall: UCallExpression?): LoggerType? {
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

    internal fun isGuarded(call: UCallExpression): Boolean {
      val loggerLevel = getLoggerLevel(call) ?: return false
      val guardedCondition = getGuardedCondition(call) ?: return false
      val levelFromCondition = getLevelFromCondition(guardedCondition) ?: return false
      return isGuardedIn(levelFromCondition, loggerLevel)
    }

    internal fun isGuardedIn(levelFromCondition: LevelType, loggerLevel: LevelType): Boolean {
      return levelFromCondition == loggerLevel
    }

    internal fun isLegacyGuardedIn(levelFromCondition: LegacyLevelType, loggerLevel: LegacyLevelType): Boolean {
      return levelFromCondition == loggerLevel
    }

    internal fun getLegacyLevelFromCondition(condition: UExpression): LegacyLevelType? {
      if (condition is UCallExpression) {
        val methodName = condition.methodName ?: return null
        if ("isEnabledFor" == methodName || "isLoggable" == methodName) {
          return findLevelTypeByFirstArgument(condition, LEGACY_LEVEL_CLASSES, LEGACY_LEVEL_MAP)
        }
        return levelTypeFromGuard(methodName, LEGACY_LEVEL_MAP)
      }
      if (condition is UQualifiedReferenceExpression) {
        if (condition.selector is UCallExpression) {
          return getLegacyLevelFromCondition(condition.selector)
        }
        val methodName = condition.resolvedName ?: return null
        return levelTypeFromGuard(methodName, LEGACY_LEVEL_MAP)
      }
      return null
    }

    internal fun getLevelFromCondition(condition: UExpression): LevelType? {
      if (condition is UCallExpression) {
        val methodName = condition.methodName ?: return null
        if ("isEnabled" == methodName || "isEnabledForLevel" == methodName) {
          return findLevelTypeByFirstArgument(condition, LEVEL_CLASSES, LEVEL_MAP)
        }
        return levelTypeFromGuard(methodName, LEVEL_MAP)
      }
      if (condition is UQualifiedReferenceExpression) {
        if (condition.selector is UCallExpression) {
          return getLevelFromCondition(condition.selector)
        }
        val methodName = condition.resolvedName ?: return null
        return levelTypeFromGuard(methodName, LEVEL_MAP)
      }
      return null
    }

    internal fun getGuardedCondition(call: UCallExpression?): UExpression? {
      if (call == null) return null
      val loggerSource = getLoggerQualifier(call) ?: return null
      var ifExpression: UIfExpression? = call.getParentOfType<UIfExpression>() ?: return null
      while (ifExpression != null) {
        if (getReferencesForVariable(loggerSource, ifExpression.condition).isEmpty()) {
          ifExpression = ifExpression.getParentOfType<UIfExpression>() ?: return null
          continue
        }
        break
      }
      if (ifExpression == null) return null
      var condition = ifExpression.condition.skipParenthesizedExprDown()
      if (condition is UPrefixExpression) {
        if (condition.operator != UastPrefixOperator.LOGICAL_NOT) return null
        val elseExpression = ifExpression.elseExpression
        if (elseExpression == null || !isPsiAncestor(elseExpression, call)) return null
        condition = condition.operand
      }
      else {
        val thenExpression = ifExpression.thenExpression
        if (thenExpression == null || !isPsiAncestor(thenExpression, call)) return null
      }
      return getGuardedCondition(condition, loggerSource)
    }

    private fun getReferencesForVariable(variable: UElement, context: UElement): List<UQualifiedReferenceExpression> {
      val sourcePsi = variable.sourcePsi ?: return emptyList()
      val result = mutableListOf<UQualifiedReferenceExpression>()
      val visitor = object : AbstractUastVisitor() {
        override fun visitQualifiedReferenceExpression(node: UQualifiedReferenceExpression): Boolean {
          val selector = node.receiver
          val resolveToUElement = (selector as? UResolvable)?.resolveToUElement() ?: return true
          if (sourcePsi.isEquivalentTo(resolveToUElement.sourcePsi)) {
            result.add(node)
          }
          return true
        }
      }
      context.accept(visitor)
      return result
    }

    private fun getGuardedCondition(condition: UExpression, loggerSource: UElement): UExpression? {
      if (condition is UCallExpression) {
        if ((condition.receiver as? UResolvable)?.resolveToUElement()?.sourcePsi != loggerSource.sourcePsi) {
          return null
        }
        return condition
      }
      if (condition is UQualifiedReferenceExpression) {
        if ((condition.receiver as? UResolvable)?.resolveToUElement()?.sourcePsi != loggerSource.sourcePsi) {
          return null
        }
        if (condition.selector is UCallExpression) {
          return getGuardedCondition(condition.selector, loggerSource)
        }
        return condition
      }
      if (condition is UPolyadicExpression && condition.operator == UastBinaryOperator.LOGICAL_AND) {
        for (operand in condition.operands) {
          val nestedCondition = getGuardedCondition(operand, loggerSource)
          if (nestedCondition != null) return nestedCondition
        }
      }
      return null
    }

    private fun <T> levelTypeFromGuard(methodName: String, levelMap: Map<String, T>): T? {
      if (!methodName.startsWith("is") || !methodName.endsWith("Enabled")) {
        return null
      }
      val levelInfo = methodName.substring(2, methodName.length - 7).uppercase()
      return levelMap[levelInfo]
    }

    private fun getLoggerQualifier(call: UCallExpression?): UElement? {
      if (call == null) return null
      var receiver = call.receiver?.skipParenthesizedExprDown()
      if (receiver is UCallExpression) {
        receiver = receiver.receiver
      }
      if (receiver is UQualifiedReferenceExpression) {
        receiver = receiver.receiver
      }
      if (receiver is USimpleNameReferenceExpression) {
        val resolvedReceiver = receiver.resolveToUElement()
        if (resolvedReceiver is UVariable) {
          val loggerVariable = resolvedReceiver as? UVariable ?: return null
          val type = loggerVariable.type
          val canonicalText = type.canonicalText
          if (LOGGER_CLASSES.contains(canonicalText) || LEGACY_LOGGER_CLASSES.contains(canonicalText)) {
            return loggerVariable
          }
          if (type.equalsToText(SLF4J_EVENT_BUILDER) || type.equalsToText(LOG4J_LOG_BUILDER)) {
            val uastInitializer = (loggerVariable.uastInitializer as? UQualifiedReferenceExpression) ?: return null
            return getLoggerQualifier(uastInitializer.selector as? UCallExpression)
          }
        }
        if (resolvedReceiver is UMethod) {
          if (!resolvedReceiver.uastParameters.isEmpty()) return null
          val methodType = resolvedReceiver.returnType?.canonicalText ?: return null
          if (LOGGER_CLASSES.contains(methodType) || LEGACY_LOGGER_CLASSES.contains(methodType)) {
            return resolvedReceiver
          }
        }
      }
      return null
    }

    internal fun getLegacyLoggerLevel(uCall: UCallExpression?): LegacyLevelType? {
      if (uCall == null) {
        return null
      }
      val methodName = uCall.methodName
      if (LEGACY_METHODS_WITH_LEVEL.contains(methodName)) {
        val levelTypeFromLog = findLevelTypeByFirstArgument(uCall, LEGACY_LEVEL_CLASSES, LEGACY_LEVEL_MAP)
        if (levelTypeFromLog != null) {
          return levelTypeFromLog
        }
      }
      if (methodName == null) {
        return null
      }
      return findLevelTypeByName(methodName, LEGACY_LEVEL_MAP)
    }

    internal fun getLoggerLevel(uCall: UCallExpression?, isLog: Boolean = false): LevelType? {
      if (uCall == null) {
        return null
      }

      var levelName = uCall.methodName
      if (isLog || "log" == levelName) {
        val levelTypeFromLog = findLevelTypeByFirstArgument(uCall, LEVEL_CLASSES, LEVEL_MAP)
        if (levelTypeFromLog != null) {
          return levelTypeFromLog
        }
        var receiver = uCall.receiver ?: return null
        if (receiver is UQualifiedReferenceExpression) {
          receiver = receiver.selector
        }
        else if (receiver is USimpleNameReferenceExpression) {
          val variable = receiver.resolveToUElement() as? UVariable ?: return null
          receiver = (variable.uastInitializer as? UQualifiedReferenceExpression)?.selector ?: return null
        }
        val nextCall = receiver as? UCallExpression
        if ("atLevel" == nextCall?.methodName) {
          val levelTypeFromAtLevel = findLevelTypeByFirstArgument(nextCall, LEVEL_CLASSES, LEVEL_MAP)
          if (levelTypeFromAtLevel != null) {
            return levelTypeFromAtLevel
          }
        }
        val loggerLevel = getLoggerLevel(nextCall, true)
        if (loggerLevel != null) return loggerLevel
        levelName = nextCall?.methodName
      }
      if (levelName == null) {
        return null
      }
      return findLevelTypeByName(levelName, LEVEL_MAP)
    }

    private fun <T> findLevelTypeByFirstArgument(uCall: UCallExpression, levelClasses: Set<String>, levelMap: Map<String, T>): T? {
      val valueArguments = uCall.valueArguments
      if (valueArguments.isNotEmpty()) {
        val firstArgument = valueArguments[0]
        if (firstArgument is UReferenceExpression) {
          val method = uCall.resolveToUElement() as? UMethod ?: return null
          val parameters = method.uastParameters
          if (parameters.isEmpty()) {
            return null
          }
          val firstParameter = parameters[0]
          if (levelClasses.none { InheritanceUtil.isInheritor(firstParameter.type, it) }) {
            return null
          }
          val levelField = firstArgument.resolveToUElement() as? UField ?: return null
          return findLevelTypeByName(levelField.name, levelMap)
        }
      }
      return null
    }

    private fun <T> findLevelTypeByName(levelName: String, levelMap: Map<String, T>): T? {
      var convertedLevelName = levelName.uppercase()
      var result = levelMap[convertedLevelName]
      if (result != null) return result
      if (convertedLevelName.startsWith("AT")) {
        convertedLevelName = convertedLevelName.substring(2)
        result = levelMap[convertedLevelName]
        if (result != null) return result
      }
      return null
    }

    internal fun countPlaceHolders(text: String, loggerType: LoggerType?): Int {
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

    fun getLoggerCalls(guardedCondition: UExpression): List<UCallExpression> {
      val sourcePsi = guardedCondition.sourcePsi ?: return emptyList()
      return CachedValuesManager.getManager(sourcePsi.project).getCachedValue(sourcePsi, CachedValueProvider {
        val emptyResult = CachedValueProvider.Result.create(listOf<UCallExpression>(), PsiModificationTracker.MODIFICATION_COUNT)
        val qualifier = when (val guarded = sourcePsi.toUElementOfType<UExpression>()) {
          is UQualifiedReferenceExpression -> {
            (guarded.receiver as? UResolvable)?.resolveToUElement() as? UVariable
          }
          is UCallExpression -> {
            (guarded.receiver as? UResolvable)?.resolveToUElement() as? UVariable
          }
          else -> {
            null
          }
        }
        if (qualifier == null) {
          return@CachedValueProvider emptyResult
        }
        val uIfExpression = guardedCondition.getParentOfType<UIfExpression>()
        if (uIfExpression == null) {
          return@CachedValueProvider emptyResult
        }
        val referencesForVariable = getReferencesForVariable(qualifier, uIfExpression)
        val filtered = referencesForVariable.mapNotNull { it.selector as? UCallExpression }
          .filter { it.sourcePsi?.containingFile != null }
          .filter { LOG_MATCHERS.uCallMatches(it) || LEGACY_LOG_MATCHERS.uCallMatches(it) }
        return@CachedValueProvider CachedValueProvider.Result.create(filtered,
                                                                     PsiModificationTracker.MODIFICATION_COUNT)
      })
    }

    enum class LoggerType {
      SLF4J_LOGGER_TYPE, SLF4J_BUILDER_TYPE, LOG4J_LOGGER_TYPE, LOG4J_BUILDER_TYPE
    }

    enum class LevelType {
      FATAL, ERROR, WARN, INFO, DEBUG, TRACE
    }

    @Suppress("unused")
    enum class LegacyLevelType {
      FATAL, ERROR, SEVERE, WARN, WARNING, INFO, DEBUG, TRACE, CONFIG, FINE, FINER, FINEST
    }

    internal val GUARD_MAP = mapOf(
      Pair("isTraceEnabled", "trace"),
      Pair("isDebugEnabled", "debug"),
      Pair("isInfoEnabled", "info"),
      Pair("isWarnEnabled", "warn"),
      Pair("isErrorEnabled", "error"),
      Pair("isFatalEnabled", "fatal"),
    )
  }
}