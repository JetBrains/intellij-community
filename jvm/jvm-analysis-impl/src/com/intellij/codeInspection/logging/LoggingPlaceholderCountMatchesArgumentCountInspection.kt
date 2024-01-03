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
import com.siyeh.ig.format.FormatDecode
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor
import org.jetbrains.uast.visitor.AbstractUastVisitor


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
        val index = getLogStringIndex(parameters) ?: return true

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
        PlaceholderLoggerType.SLF4J -> { //according to the reference, an exception should not have a placeholder
          argumentCount = if (lastArgumentIsException) argumentCount - 1 else argumentCount
          if (placeholderCountHolder.status == PlaceholdersStatus.PARTIAL) {
            if (placeholderCountHolder.count <= argumentCount) ResultType.SUCCESS else ResultType.PARTIAL_PLACE_HOLDER_MISMATCH
          }
          else {
            if (placeholderCountHolder.count == argumentCount) ResultType.SUCCESS else ResultType.PLACE_HOLDER_MISMATCH
          }
        }
        PlaceholderLoggerType.SLF4J_EQUAL_PLACEHOLDERS, PlaceholderLoggerType.LOG4J_EQUAL_PLACEHOLDERS, PlaceholderLoggerType.AKKA_PLACEHOLDERS -> {
          if (placeholderCountHolder.status == PlaceholdersStatus.PARTIAL) {
            if (placeholderCountHolder.count <= argumentCount) ResultType.SUCCESS else ResultType.PARTIAL_PLACE_HOLDER_MISMATCH
          }
          else {
            if (placeholderCountHolder.count == argumentCount) ResultType.SUCCESS else ResultType.PLACE_HOLDER_MISMATCH
          }
        }
        PlaceholderLoggerType.LOG4J_OLD_STYLE,
        PlaceholderLoggerType.LOG4J_FORMATTED_STYLE -> { // if there is more than one argument and the last argument is an exception, but there is a placeholder for
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

    private fun solvePlaceholderCount(loggerType: PlaceholderLoggerType,
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

    private fun countPlaceholders(holders: List<LoggingStringPartEvaluator.PartHolder>, loggerType: PlaceholderLoggerType): PlaceholderCountResult {
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
              (loggerType == PlaceholderLoggerType.SLF4J_EQUAL_PLACEHOLDERS || loggerType == PlaceholderLoggerType.SLF4J)) {
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
  }

  private enum class ResultType {
    PARTIAL_PLACE_HOLDER_MISMATCH, PLACE_HOLDER_MISMATCH, INCORRECT_STRING, SUCCESS
  }

  enum class Slf4jToLog4J2Type {
    AUTO, YES, NO
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
