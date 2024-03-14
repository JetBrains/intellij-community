// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.logging

import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.util.InheritanceUtil
import com.intellij.util.containers.addIfNotNull
import com.siyeh.ig.callMatcher.CallMapper
import com.siyeh.ig.callMatcher.CallMatcher
import com.siyeh.ig.format.FormatDecode
import com.siyeh.ig.psiutils.TypeUtils
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastVisitor

const val MAX_BUILDER_LENGTH = 20
const val ADD_ARGUMENT_METHOD_NAME = "addArgument"
const val SET_MESSAGE_METHOD_NAME = "setMessage"

fun getLogStringIndex(parameters: List<UParameter>): Int? {
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

fun detectLoggerBuilderMethod(uCallExpression: UCallExpression): UCallExpression? {
  val uQualifiedReferenceExpression = uCallExpression.getOutermostQualified() ?: return null
  val selector = uQualifiedReferenceExpression.selector as? UCallExpression ?: return null
  if (LOGGER_BUILDER_LOG_TYPE_SEARCHERS.mapFirst(selector) == null) return null
  return selector
}

private val SLF4J_HOLDER = object : LoggerTypeSearcher {
  override fun findType(expression: UCallExpression, context: LoggerContext): PlaceholderLoggerType {
    return if (context.log4jAsImplementationForSlf4j) { //use old style as more common
      PlaceholderLoggerType.LOG4J_OLD_STYLE
    }
    else PlaceholderLoggerType.SLF4J
  }
}

val LOG4J_LOG_BUILDER_HOLDER = object : LoggerTypeSearcher {
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


val SLF4J_BUILDER_HOLDER = object : LoggerTypeSearcher {
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
      if (sourcePsi is SyntheticElement) {
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

val IDEA_PLACEHOLDERS = object : LoggerTypeSearcher {
  override fun findType(expression: UCallExpression, context: LoggerContext): PlaceholderLoggerType? {
    return null
  }
}

class LoggerContext(val log4jAsImplementationForSlf4j: Boolean)

interface LoggerTypeSearcher {
  fun findType(expression: UCallExpression, context: LoggerContext): PlaceholderLoggerType?
}

private fun getImmediateLoggerQualifier(expression: UCallExpression): UExpression? {
  val result = expression.receiver?.skipParenthesizedExprDown()
  if (result is UQualifiedReferenceExpression) {
    return result.selector
  }
  return result
}

val LOGGER_BUILDER_LOG_TYPE_SEARCHERS: CallMapper<LoggerTypeSearcher> = CallMapper<LoggerTypeSearcher>()
  .register(CallMatcher.instanceCall(LoggingUtil.SLF4J_EVENT_BUILDER, "log"), SLF4J_BUILDER_HOLDER)
  .register(CallMatcher.instanceCall(LoggingUtil.LOG4J_LOG_BUILDER, "log"), LOG4J_LOG_BUILDER_HOLDER)

val LOGGER_RESOLVE_TYPE_SEARCHERS: CallMapper<LoggerTypeSearcher> = LOGGER_BUILDER_LOG_TYPE_SEARCHERS
  .register(CallMatcher.instanceCall(LoggingUtil.SLF4J_LOGGER, "trace", "debug", "info", "warn", "error"), SLF4J_HOLDER)
  .register(CallMatcher.instanceCall(LoggingUtil.IDEA_LOGGER, "trace", "debug", "info", "warn", "error"), IDEA_PLACEHOLDERS)
  .register(CallMatcher.instanceCall(LoggingUtil.LOG4J_LOGGER, "trace", "debug", "info", "warn", "error", "fatal", "log"), LOG4J_HOLDER)

val LOGGER_TYPE_SEARCHERS: CallMapper<LoggerTypeSearcher> = LOGGER_RESOLVE_TYPE_SEARCHERS
  .register(CallMatcher.instanceCall(LoggingUtil.AKKA_LOGGING, "debug", "error", "format", "info", "log", "warning"), AKKA_PLACEHOLDERS)


private val BUILDER_CHAIN = setOf("addKeyValue", "addMarker", "setCause")


fun findMessageSetterStringArg(node: UCallExpression,
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
    if (methodName == SET_MESSAGE_METHOD_NAME && currentCall.valueArgumentCount == 1) {
      val uExpression = currentCall.valueArguments[0]
      if (!TypeUtils.isJavaLangString(uExpression.getExpressionType())) {
        return null
      }
      return uExpression
    }
    if (BUILDER_CHAIN.contains(methodName) || methodName == ADD_ARGUMENT_METHOD_NAME) {
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
fun findAdditionalArgumentCount(node: UCallExpression,
                                loggerType: LoggerTypeSearcher,
                                allowIntermediateMessage: Boolean): List<UExpression>? {
  val uExpressions = mutableListOf<UExpression>()
  if (loggerType != SLF4J_BUILDER_HOLDER) {
    return emptyList()
  }
  var currentCall = node.receiver
  for (ignore in 0..MAX_BUILDER_LENGTH) {
    if (currentCall is UQualifiedReferenceExpression) {
      currentCall = currentCall.selector
    }
    if (currentCall is UCallExpression) {
      val methodName = currentCall.methodName ?: return null
      if (methodName == ADD_ARGUMENT_METHOD_NAME) {
        uExpressions.add(currentCall.valueArguments.first())
        currentCall = currentCall.receiver
        continue
      }
      if (methodName.startsWith("at") && LoggingUtil.getLoggerLevel(currentCall) != null) {
        return uExpressions
      }
      if (BUILDER_CHAIN.contains(methodName) || (allowIntermediateMessage && methodName == SET_MESSAGE_METHOD_NAME)) {
        currentCall = currentCall.receiver
        continue
      }
      return null
    }
    return null
  }
  return null
}

fun solvePlaceholderCount(loggerType: PlaceholderLoggerType,
                          argumentCount: Int,
                          holders: List<LoggingStringPartEvaluator.PartHolder>): PlaceholderCountResult {
  return if (loggerType == PlaceholderLoggerType.LOG4J_FORMATTED_STYLE) {
    val prefix = StringBuilder()
    var full = true
    for (holder in holders) {
      if (holder.isConstant && holder.text != null) {
        prefix.append(holder.text)
      }
      else {
        full = false
        break
      }
    }
    if (prefix.isEmpty()) {
      return PlaceholderCountResult(emptyList(), PlaceholdersStatus.EMPTY)
    }
    val validators = try {
      if (full) {
        FormatDecode.decode(prefix.toString(), argumentCount)
      }
      else {
        FormatDecode.decodePrefix(prefix.toString(), argumentCount)
      }
    }
    catch (e: FormatDecode.IllegalFormatException) {
      return PlaceholderCountResult(emptyList(), PlaceholdersStatus.ERROR_TO_PARSE_STRING)
    }
    PlaceholderCountResult(listOf(
      PlaceholderRangesInPartHolder(validators.map {
        it.range
      })
    ), if (full) PlaceholdersStatus.EXACTLY else PlaceholdersStatus.PARTIAL)
  }
  else {
    countPlaceholders(holders, loggerType)
  }
}

private fun countPlaceholders(holders: List<LoggingStringPartEvaluator.PartHolder>, loggerType: PlaceholderLoggerType): PlaceholderCountResult {
  var count = 0
  var full = true
  val partHolderPlaceholderList = mutableListOf<PlaceholderRangesInPartHolder>()
  for (holderIndex in holders.indices) {
    val partHolder = holders[holderIndex]
    if (!partHolder.isConstant) {
      full = false
      continue
    }
    val string = partHolder.text ?: continue
    val length = string.length
    var escaped = false
    var lastPlaceholderIndex = -1
    val placeholderRanges = mutableListOf<TextRange>()
    for (i in 0 until length) {
      val c = string[i]
      if (c == '\\' &&
          (loggerType == PlaceholderLoggerType.SLF4J_EQUAL_PLACEHOLDERS || loggerType == PlaceholderLoggerType.SLF4J)) {
        escaped = !escaped
      }
      else if (c == '{') {
        if (holderIndex != 0 && i == 0 && !holders[holderIndex - 1].isConstant) {
          continue
        }
        if (!escaped) {
          lastPlaceholderIndex = i
        }
      }
      else if (c == '}') {
        if (lastPlaceholderIndex != -1) {
          count++
          placeholderRanges.add(TextRange(lastPlaceholderIndex, lastPlaceholderIndex + 2))
          lastPlaceholderIndex = -1
        }
      }
      else {
        escaped = false
        lastPlaceholderIndex = -1
      }
    }
    partHolderPlaceholderList.add(PlaceholderRangesInPartHolder(placeholderRanges))
  }
  return PlaceholderCountResult(partHolderPlaceholderList, if (full) PlaceholdersStatus.EXACTLY else PlaceholdersStatus.PARTIAL)
}

private fun couldBeThrowableSupplier(loggerType: PlaceholderLoggerType, lastParameter: UParameter?, lastArgument: UExpression?): Boolean {
  if (loggerType != PlaceholderLoggerType.LOG4J_OLD_STYLE && loggerType != PlaceholderLoggerType.LOG4J_FORMATTED_STYLE) {
    return false
  }
  if (lastParameter == null || lastArgument == null) {
    return false
  }
  val lastParameterType = lastParameter.type.let { if (it is PsiEllipsisType) it.componentType else it }
  if (lastParameterType is UastErrorType) {
    return false
  }
  if (!(InheritanceUtil.isInheritor(lastParameterType, CommonClassNames.JAVA_UTIL_FUNCTION_SUPPLIER) || InheritanceUtil.isInheritor(
      lastParameterType, "org.apache.logging.log4j.util.Supplier"))) {
    return false
  }
  val sourcePsi = lastArgument.sourcePsi ?: return true
  val throwable = PsiType.getJavaLangThrowable(sourcePsi.manager, sourcePsi.resolveScope)

  if (lastArgument is ULambdaExpression) {
    return !lastArgument.getReturnExpressions().any {
      val expressionType = it.getExpressionType()
      expressionType != null && !throwable.isConvertibleFrom(expressionType)
    }
  }

  if (lastArgument is UCallableReferenceExpression) {
    val psiType = lastArgument.getMethodReferenceReturnType() ?: return true
    return throwable.isConvertibleFrom(psiType)
  }

  val type = lastArgument.getExpressionType() ?: return true
  val functionalReturnType = LambdaUtil.getFunctionalInterfaceReturnType(type) ?: return true
  return throwable.isConvertibleFrom(functionalReturnType)
}


fun getPlaceholderContext(
  uCallExpression: UCallExpression,
  mapper: CallMapper<LoggerTypeSearcher>,
  log4jAsImplementationForSlf4j: Boolean
): PlaceholderContext? {
  val method = uCallExpression.resolveToUElement() as? UMethod ?: return null

  val searcher = mapper.mapFirst(uCallExpression) ?: return null
  val arguments = uCallExpression.valueArguments

  val loggerType = searcher.findType(uCallExpression, LoggerContext(log4jAsImplementationForSlf4j)) ?: return null

  if (arguments.isEmpty() && searcher != SLF4J_BUILDER_HOLDER) return null
  val parameters = method.uastParameters
  var placeholderParameters: List<UExpression>?
  val logStringArgument: UExpression?
  var lastArgumentIsException = false
  var lastArgumentIsSupplier = false
  if (parameters.isEmpty() || arguments.isEmpty()) {
    //try to find String somewhere else
    logStringArgument = findMessageSetterStringArg(uCallExpression, searcher) ?: return null
    placeholderParameters = findAdditionalArgumentCount(uCallExpression, searcher, true) ?: return null
  }
  else {
    val index = getLogStringIndex(parameters) ?: return null

    placeholderParameters = arguments.subList(index, arguments.size)
    lastArgumentIsException = hasThrowableType(arguments[arguments.size - 1])
    lastArgumentIsSupplier = couldBeThrowableSupplier(loggerType, parameters[parameters.size - 1], arguments[arguments.size - 1])

    if (placeholderParameters.size == 1 && parameters.size > 1) {
      val argument = arguments[index]
      val argumentType = argument.getExpressionType()
      if (argumentType is PsiArrayType) {
        return null
      }
    }
    val additionalArgumentCount = findAdditionalArgumentCount(uCallExpression, searcher, false) ?: return null
    placeholderParameters += additionalArgumentCount
    logStringArgument = arguments[index - 1]
  }

  val parts = collectParts(logStringArgument) ?: return null

  return PlaceholderContext(
    placeholderParameters.sortedBy { it.textRange?.startOffset },
    logStringArgument,
    parts,
    loggerType,
    lastArgumentIsException,
    lastArgumentIsSupplier
  )
}

fun collectParts(logStringArgument: UExpression): List<LoggingStringPartEvaluator.PartHolder>? {
  return LoggingStringPartEvaluator.calculateValue(logStringArgument)
}

fun hasThrowableType(lastArgument: UExpression): Boolean {
  val type = lastArgument.getExpressionType()
  if (type is UastErrorType) {
    return false
  }
  if (type is PsiDisjunctionType) {
    return type.disjunctions.all { InheritanceUtil.isInheritor(it, CommonClassNames.JAVA_LANG_THROWABLE) }
  }
  return InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_LANG_THROWABLE)
}

private fun UCallableReferenceExpression.getMethodReferenceReturnType(): PsiType? {
  val method = this.resolveToUElement() as? UMethod ?: return null
  if (method.isConstructor) {
    val psiMethod = method.javaPsi
    val containingClass = psiMethod.containingClass ?: return null
    return JavaPsiFacade.getElementFactory(containingClass.project).createType(containingClass)
  }
  return method.returnType
}

private fun ULambdaExpression.getReturnExpressions(): List<UExpression> {
  val returnExpressions = mutableListOf<UExpression>()
  val visitor: AbstractUastVisitor = object : AbstractUastVisitor() {
    override fun visitReturnExpression(node: UReturnExpression): Boolean {
      returnExpressions.addIfNotNull(node.returnExpression)
      return true
    }
  }
  body.accept(visitor)
  return returnExpressions
}

enum class ResultType {
  PARTIAL_PLACE_HOLDER_MISMATCH, PLACE_HOLDER_MISMATCH, INCORRECT_STRING, SUCCESS
}


enum class PlaceholderLoggerType {
  SLF4J, SLF4J_EQUAL_PLACEHOLDERS, LOG4J_OLD_STYLE, LOG4J_FORMATTED_STYLE, LOG4J_EQUAL_PLACEHOLDERS, AKKA_PLACEHOLDERS
}

enum class PlaceholdersStatus {
  EXACTLY, PARTIAL, ERROR_TO_PARSE_STRING, EMPTY
}


/**
 * A data class representing the result of a placeholder count operation.
 *
 * @property placeholderRangesInPartHolderList   The list of [PlaceholderRangesInPartHolder] instances.
 * @property status                              The status of the placeholders ranges extraction.
 *
 * @see countPlaceholders
 */
data class PlaceholderCountResult(val placeholderRangesInPartHolderList: List<PlaceholderRangesInPartHolder>, val status: PlaceholdersStatus) {
  val count = placeholderRangesInPartHolderList.sumOf { it.rangeList.size }
}

/**
 * A data class representing ranges of placeholders ({}, or %s) within the [LoggingStringPartEvaluator.PartHolder].
 */
data class PlaceholderRangesInPartHolder(val rangeList: List<TextRange?>)

/**
 * A data class representing the context for a placeholder of the logger.
 *
 * @property placeholderParameters   The list of [UExpression] representing the parameters for the placeholder.
 * @property logStringArgument       The [UExpression] representing the string argument for logging.
 * @property partHolderList          The list of PartHolder from the [LoggingStringPartEvaluator].
 * @property loggerType              The type of logger used [PlaceholderLoggerType].
 * @property lastArgumentIsException Indicates whether the last argument of logger method is an exception or not.
 * @property lastArgumentIsSupplier  Indicates whether the last argument of logger method is a supplier or not.
 *
 * @see getPlaceholderContext
 */
data class PlaceholderContext(
  val placeholderParameters: List<UExpression>,
  val logStringArgument: UExpression,
  val partHolderList: List<LoggingStringPartEvaluator.PartHolder>,
  val loggerType: PlaceholderLoggerType,
  val lastArgumentIsException: Boolean,
  val lastArgumentIsSupplier: Boolean,
)