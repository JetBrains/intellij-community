// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.logging

import com.intellij.analysis.JvmAnalysisBundle
import com.intellij.codeInspection.AbstractBaseUastLocalInspectionTool
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.options.OptPane
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.java.library.JavaLibraryUtil
import com.intellij.psi.*
import com.intellij.psi.util.InheritanceUtil
import com.intellij.uast.UastHintedVisitorAdapter
import com.intellij.util.containers.addIfNotNull
import com.siyeh.ig.callMatcher.CallMapper
import com.siyeh.ig.callMatcher.CallMatcher
import com.siyeh.ig.format.FormatDecode
import com.siyeh.ig.psiutils.TypeUtils
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor
import org.jetbrains.uast.visitor.AbstractUastVisitor

private const val MAX_BUILDER_LENGTH = 20

class LoggingPlaceholderCountMatchesArgumentCountInspection : AbstractBaseUastLocalInspectionTool() {

  @JvmField
  var slf4jToLog4J2Type = Slf4jToLog4J2Type.AUTO

  override fun getOptionsPane(): OptPane {
    return OptPane.pane(
      OptPane.dropdown("slf4jToLog4J2Type",
                       JvmAnalysisBundle.message("jvm.inspection.logging.placeholder.count.matches.argument.count.slf4j.throwable.option"),
                       OptPane.option(Slf4jToLog4J2Type.AUTO,
                                      JvmAnalysisBundle.message(
                                        "jvm.inspection.logging.placeholder.count.matches.argument.count.slf4j.throwable.option.auto")),
                       OptPane.option(Slf4jToLog4J2Type.NO,
                                      JvmAnalysisBundle.message(
                                        "jvm.inspection.logging.placeholder.count.matches.argument.count.slf4j.throwable.option.no")),
                       OptPane.option(Slf4jToLog4J2Type.YES,
                                      JvmAnalysisBundle.message(
                                        "jvm.inspection.logging.placeholder.count.matches.argument.count.slf4j.throwable.option.yes"))))

  }

  override fun buildVisitor(holder: ProblemsHolder,
                            isOnTheFly: Boolean,
                            session: LocalInspectionToolSession): PsiElementVisitor = UastHintedVisitorAdapter.create(holder.file.language,
                                                                                                                      PlaceholderCountMatchesArgumentCountVisitor(
                                                                                                                        holder), arrayOf(
    UCallExpression::class.java), directOnly = true)

  inner class PlaceholderCountMatchesArgumentCountVisitor(
    private val holder: ProblemsHolder,
  ) : AbstractUastNonRecursiveVisitor() {

    private fun hasBridgeFromSlf4jToLog4j2(element: UElement): Boolean {
      val file = element.getContainingUFile() ?: return true
      val sourcePsi = file.sourcePsi
      val project = sourcePsi.project
      return JavaLibraryUtil.hasLibraryClass(project, LoggingUtil.LOG_4_J_LOGGER)
    }

    override fun visitCallExpression(node: UCallExpression): Boolean {
      val searcher = LOGGER_TYPE_SEARCHERS.mapFirst(node) ?: return true

      val arguments = node.valueArguments

      if (arguments.isEmpty() && searcher != SLF4J_BUILDER_HOLDER) return true

      val log4jAsImplementationForSlf4j = when (slf4jToLog4J2Type) {
        Slf4jToLog4J2Type.AUTO -> hasBridgeFromSlf4jToLog4j2(node)
        Slf4jToLog4J2Type.YES -> true
        Slf4jToLog4J2Type.NO -> false
      }
      val loggerType = searcher.findType(node, LoggerContext(log4jAsImplementationForSlf4j)) ?: return true

      val method = node.resolveToUElement() as? UMethod ?: return true
      val parameters = method.uastParameters
      var argumentCount: Int?
      val logStringArgument: UExpression?
      var lastArgumentIsException = false
      var lastArgumentIsSupplier = false
      if (parameters.isEmpty() || arguments.isEmpty()) {
        //try to find String somewhere else
        logStringArgument = findMessageSetterStringArg(node, searcher) ?: return true
        argumentCount = findAdditionalArgumentCount(node, searcher, true) ?: return true
      }
      else {
        val index = getIndex(parameters) ?: return true

        argumentCount = arguments.size - index
        lastArgumentIsException = hasThrowableType(arguments[arguments.size - 1])
        lastArgumentIsSupplier = couldBeThrowableSupplier(loggerType, parameters[parameters.size - 1], arguments[arguments.size - 1])

        if (argumentCount == 1 && parameters.size > 1) {
          val argument = arguments[index]
          val argumentType = argument.getExpressionType()
          if (argumentType is PsiArrayType) {
            return true
          }
        }
        val additionalArgumentCount: Int = findAdditionalArgumentCount(node, searcher, false) ?: return true
        argumentCount += additionalArgumentCount
        logStringArgument = arguments[index - 1]
      }

      val parts = collectParts(logStringArgument) ?: return true

      val placeholderCountHolder = solvePlaceholderCount(loggerType, argumentCount, parts)
      if (placeholderCountHolder.status == PlaceholdersStatus.EMPTY) {
        return true
      }
      if (placeholderCountHolder.status == PlaceholdersStatus.ERROR_TO_PARSE_STRING) {
        registerProblem(holder, logStringArgument, Result(argumentCount, 0, ResultType.INCORRECT_STRING))
        return true
      }


      val resultType = when (loggerType) {
        LoggerType.SLF4J -> { //according to the reference, an exception should not have a placeholder
          argumentCount = if (lastArgumentIsException) argumentCount - 1 else argumentCount
          if (placeholderCountHolder.status == PlaceholdersStatus.PARTIAL) {
            if (placeholderCountHolder.count <= argumentCount) ResultType.SUCCESS else ResultType.PARTIAL_PLACE_HOLDER_MISMATCH
          }
          else {
            if (placeholderCountHolder.count == argumentCount) ResultType.SUCCESS else ResultType.PLACE_HOLDER_MISMATCH
          }
        }
        LoggerType.SLF4J_EQUAL_PLACEHOLDERS, LoggerType.LOG4J_EQUAL_PLACEHOLDERS, LoggerType.AKKA_PLACEHOLDERS -> {
          if (placeholderCountHolder.status == PlaceholdersStatus.PARTIAL) {
            if (placeholderCountHolder.count <= argumentCount) ResultType.SUCCESS else ResultType.PARTIAL_PLACE_HOLDER_MISMATCH
          }
          else {
            if (placeholderCountHolder.count == argumentCount) ResultType.SUCCESS else ResultType.PLACE_HOLDER_MISMATCH
          }
        }
        LoggerType.LOG4J_OLD_STYLE, LoggerType.LOG4J_FORMATTED_STYLE -> { // if there is more than one argument and the last argument is an exception, but there is a placeholder for
          // the exception, then the stack trace won't be logged.
          val type: ResultType
          if (placeholderCountHolder.status == PlaceholdersStatus.PARTIAL) {
            type = if ((placeholderCountHolder.count <= argumentCount && (!lastArgumentIsException || argumentCount > 1)) || (lastArgumentIsException && placeholderCountHolder.count <= argumentCount - 1) || //consider the most general case
                       (lastArgumentIsSupplier && (placeholderCountHolder.count <= argumentCount))) ResultType.SUCCESS
            else ResultType.PARTIAL_PLACE_HOLDER_MISMATCH
          }
          else {
            type = if ((placeholderCountHolder.count == argumentCount && (!lastArgumentIsException || argumentCount > 1)) || (lastArgumentIsException && placeholderCountHolder.count == argumentCount - 1) || //consider the most general case
                       (lastArgumentIsSupplier && (placeholderCountHolder.count == argumentCount || placeholderCountHolder.count == argumentCount - 1))) ResultType.SUCCESS
            else ResultType.PLACE_HOLDER_MISMATCH
          }
          argumentCount = if (lastArgumentIsException) argumentCount - 1 else argumentCount
          type
        }
      }

      if (resultType == ResultType.SUCCESS) {
        return true
      }

      registerProblem(holder, logStringArgument, Result(argumentCount, placeholderCountHolder.count, resultType))

      return true
    }

    private val BUILDER_CHAIN = setOf("addKeyValue", "addMarker", "setCause")
    private val ADD_ARGUMENT = "addArgument"
    private val SET_MESSAGE = "setMessage"
    private fun findMessageSetterStringArg(node: UCallExpression,
                                           loggerType: LoggerTypeSearcher): UExpression? {
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
        if (BUILDER_CHAIN.contains(methodName)) {
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
    private fun findAdditionalArgumentCount(node: UCallExpression,
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

    private fun collectParts(logStringArgument: UExpression): List<LoggingStringPartEvaluator.PartHolder>? {
      return LoggingStringPartEvaluator.calculateValue(logStringArgument)
    }

    private fun registerProblem(holder: ProblemsHolder, logStringArgument: UExpression, result: Result) {
      val errorString = buildErrorString(result)
      val anchor = logStringArgument.sourcePsi ?: return
      holder.registerProblem(anchor, errorString)
    }

    @InspectionMessage
    private fun buildErrorString(result: Result): String {
      if (result.result == ResultType.INCORRECT_STRING) {
        return JvmAnalysisBundle.message("jvm.inspection.logging.placeholder.count.matches.argument.count.incorrect.problem.descriptor")
      }
      if (result.result == ResultType.PARTIAL_PLACE_HOLDER_MISMATCH) {
        return JvmAnalysisBundle.message("jvm.inspection.logging.placeholder.count.matches.argument.count.fewer.problem.partial.descriptor",
                                         result.argumentCount, result.placeholderCount)
      }
      return if (result.argumentCount > result.placeholderCount) JvmAnalysisBundle.message(
        "jvm.inspection.logging.placeholder.count.matches.argument.count.more.problem.descriptor", result.argumentCount,
        result.placeholderCount)
      else JvmAnalysisBundle.message("jvm.inspection.logging.placeholder.count.matches.argument.count.fewer.problem.descriptor",
                                     result.argumentCount, result.placeholderCount)
    }

    private fun solvePlaceholderCount(loggerType: LoggerType,
                                      argumentCount: Int,
                                      holders: List<LoggingStringPartEvaluator.PartHolder>): PlaceholderCountResult {
      return if (loggerType == LoggerType.LOG4J_FORMATTED_STYLE) {
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
          return PlaceholderCountResult(0, PlaceholdersStatus.EMPTY)
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
          return PlaceholderCountResult(0, PlaceholdersStatus.ERROR_TO_PARSE_STRING)
        }
        PlaceholderCountResult(validators.size, if (full) PlaceholdersStatus.EXACTLY else PlaceholdersStatus.PARTIAL)
      }
      else {
        countPlaceholders(holders, loggerType)
      }
    }

    private fun countPlaceholders(holders: List<LoggingStringPartEvaluator.PartHolder>, loggerType: LoggerType): PlaceholderCountResult {
      var count = 0
      var full = true
      for (holderIndex in holders.indices) {
        val partHolder = holders[holderIndex]
        if (!partHolder.isConstant) {
          full = false
          continue
        }
        val string = partHolder.text ?: continue
        val length = string.length
        var escaped = false
        var placeholder = false
        for (i in 0 until length) {
          val c = string[i]
          if (c == '\\' &&
              (loggerType == LoggerType.SLF4J_EQUAL_PLACEHOLDERS || loggerType == LoggerType.SLF4J)) {
            escaped = !escaped
          }
          else if (c == '{') {
            if (holderIndex != 0 && i == 0 && !holders[holderIndex - 1].isConstant) {
              continue
            }
            if (!escaped) placeholder = true
          }
          else if (c == '}') {
            if (placeholder) {
              count++
              placeholder = false
            }
          }
          else {
            escaped = false
            placeholder = false
          }
        }
      }
      return PlaceholderCountResult(count, if (full) PlaceholdersStatus.EXACTLY else PlaceholdersStatus.PARTIAL)
    }

    private fun couldBeThrowableSupplier(loggerType: LoggerType, lastParameter: UParameter?, lastArgument: UExpression?): Boolean {
      if (loggerType != LoggerType.LOG4J_OLD_STYLE && loggerType != LoggerType.LOG4J_FORMATTED_STYLE) {
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

    private fun hasThrowableType(lastArgument: UExpression): Boolean {
      val type = lastArgument.getExpressionType()
      if (type is UastErrorType) {
        return false
      }
      if (type is PsiDisjunctionType) {
        return type.disjunctions.all { InheritanceUtil.isInheritor(it, CommonClassNames.JAVA_LANG_THROWABLE) }
      }
      return InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_LANG_THROWABLE)
    }

    private fun getIndex(parameters: List<UParameter>): Int? {
      val index: Int?
      if (!TypeUtils.isJavaLangString(parameters[0].type)) {
        if (parameters.size < 2 || !TypeUtils.isJavaLangString(parameters[1].type)) {
          index = null
        }
        else {
          index = 2
        }
      }
      else {
        index = 1
      }
      return index
    }

    private val LOGGER_TYPE_SEARCHERS = CallMapper<LoggerTypeSearcher>()
      .register(CallMatcher.instanceCall(LoggingUtil.SLF4J_LOGGER, "trace", "debug", "info", "warn", "error"), SLF4J_HOLDER)
      .register(CallMatcher.instanceCall(LoggingUtil.SLF4J_EVENT_BUILDER, "log"), SLF4J_BUILDER_HOLDER)
      .register(CallMatcher.instanceCall(LoggingUtil.LOG4J_LOGGER, "trace", "debug", "info", "warn", "error", "fatal", "log"), LOG4J_HOLDER)
      .register(CallMatcher.instanceCall(LoggingUtil.LOG4J_LOG_BUILDER, "log"), LOG4J_LOG_BUILDER_HOLDER)
      .register(CallMatcher.instanceCall(LoggingUtil.AKKA_LOGGING, "debug", "error", "format", "info", "log", "warning"),
                AKKA_PLACEHOLDERS)
  }


  private interface LoggerTypeSearcher {
    fun findType(expression: UCallExpression, context: LoggerContext): LoggerType?
  }

  private val SLF4J_HOLDER = object : LoggerTypeSearcher {
    override fun findType(expression: UCallExpression, context: LoggerContext): LoggerType {
      return if (context.log4jAsImplementationForSlf4j) { //use old style as more common
        LoggerType.LOG4J_OLD_STYLE
      }
      else LoggerType.SLF4J
    }
  }

  private val LOG4J_LOG_BUILDER_HOLDER = object : LoggerTypeSearcher {
    override fun findType(expression: UCallExpression, context: LoggerContext): LoggerType? {
      var qualifierExpression = getImmediateLoggerQualifier(expression)
      if (qualifierExpression is UReferenceExpression) {
        val target: UVariable = qualifierExpression.resolveToUElement() as? UVariable ?: return null
        if (!target.isFinal) {
          return LoggerType.LOG4J_EQUAL_PLACEHOLDERS //formatted builder is really rare
        }
        qualifierExpression = target.uastInitializer?.skipParenthesizedExprDown()
      }
      if (qualifierExpression is UQualifiedReferenceExpression) {
        qualifierExpression = qualifierExpression.selector
      }
      if (qualifierExpression is UCallExpression) {
        return when (LOG4J_HOLDER.findType(qualifierExpression, context)) {
          LoggerType.LOG4J_FORMATTED_STYLE -> LoggerType.LOG4J_FORMATTED_STYLE
          LoggerType.LOG4J_OLD_STYLE -> LoggerType.LOG4J_EQUAL_PLACEHOLDERS
          else -> null
        }
      }
      return LoggerType.LOG4J_EQUAL_PLACEHOLDERS
    }
  }


  private val SLF4J_BUILDER_HOLDER = object : LoggerTypeSearcher {
    override fun findType(expression: UCallExpression, context: LoggerContext): LoggerType {
      if (context.log4jAsImplementationForSlf4j) {
        return LoggerType.SLF4J_EQUAL_PLACEHOLDERS
      }
      return LoggerType.SLF4J
    }
  }

  private val LOG4J_HOLDER = object : LoggerTypeSearcher {
    override fun findType(expression: UCallExpression, context: LoggerContext): LoggerType? {
      val qualifierExpression = getImmediateLoggerQualifier(expression)
      var initializer: UExpression? = null
      if (qualifierExpression is UReferenceExpression) {
        var resolvedToUElement = qualifierExpression.resolveToUElement()
        if (resolvedToUElement is UMethod) {
          resolvedToUElement = resolvedToUElement.sourcePsi.toUElement()
        }
        val target: UVariable = resolvedToUElement as? UVariable ?: return null
        val sourcePsi = target.sourcePsi as? PsiVariable
        // for lombok
        if (sourcePsi != null && !sourcePsi.isPhysical) {
          return LoggerType.LOG4J_OLD_STYLE
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
        LoggerType.LOG4J_FORMATTED_STYLE
      }
      else LoggerType.LOG4J_OLD_STYLE
    }

  }

  private val AKKA_PLACEHOLDERS = object : LoggerTypeSearcher {
    override fun findType(expression: UCallExpression, context: LoggerContext): LoggerType {
      return LoggerType.AKKA_PLACEHOLDERS
    }
  }

  private fun getImmediateLoggerQualifier(expression: UCallExpression): UExpression? {
    val result = expression.receiver?.skipParenthesizedExprDown()
    if (result is UQualifiedReferenceExpression) {
      return result.selector
    }
    return result
  }

  private data class LoggerContext(val log4jAsImplementationForSlf4j: Boolean)


  private enum class ResultType {
    PARTIAL_PLACE_HOLDER_MISMATCH, PLACE_HOLDER_MISMATCH, INCORRECT_STRING, SUCCESS
  }

  enum class Slf4jToLog4J2Type {
    AUTO, YES, NO
  }

  private enum class LoggerType {
    SLF4J, SLF4J_EQUAL_PLACEHOLDERS, LOG4J_OLD_STYLE, LOG4J_FORMATTED_STYLE, LOG4J_EQUAL_PLACEHOLDERS, AKKA_PLACEHOLDERS
  }


  private enum class PlaceholdersStatus {
    EXACTLY, PARTIAL, ERROR_TO_PARSE_STRING, EMPTY
  }

  private data class PlaceholderCountResult(val count: Int, val status: PlaceholdersStatus)


  private data class Result(val argumentCount: Int, val placeholderCount: Int, val result: ResultType)
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
