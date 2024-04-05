// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.logging

import com.intellij.analysis.JvmAnalysisBundle
import com.intellij.codeInspection.AbstractBaseUastLocalInspectionTool
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.options.OptPane
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.psi.PsiElementVisitor
import com.intellij.uast.UastHintedVisitorAdapter
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor


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

    override fun visitCallExpression(node: UCallExpression): Boolean {
      val log4jAsImplementationForSlf4j = when (slf4jToLog4J2Type) {
        Slf4jToLog4J2Type.AUTO -> null
        Slf4jToLog4J2Type.YES -> true
        Slf4jToLog4J2Type.NO -> false
      }

      val context = getPlaceholderContext(node, LOGGER_TYPE_SEARCHERS, log4jAsImplementationForSlf4j) ?: return true

      var finalArgumentCount = context.placeholderParameters.size

      val placeholderCountHolder = solvePlaceholderCount(context.loggerType, finalArgumentCount, context.partHolderList)

      if (placeholderCountHolder.status == PlaceholdersStatus.EMPTY) {
        return true
      }
      if (placeholderCountHolder.status == PlaceholdersStatus.ERROR_TO_PARSE_STRING) {
        registerProblem(holder, context.logStringArgument, Result(finalArgumentCount, 0, ResultType.INCORRECT_STRING))
        return true
      }

      val resultType = when (context.loggerType) {
        PlaceholderLoggerType.SLF4J -> { //according to the reference, an exception should not have a placeholder
          finalArgumentCount = if (context.lastArgumentIsException) finalArgumentCount - 1 else finalArgumentCount
          if (placeholderCountHolder.status == PlaceholdersStatus.PARTIAL) {
            if (placeholderCountHolder.count <= finalArgumentCount) ResultType.SUCCESS else ResultType.PARTIAL_PLACE_HOLDER_MISMATCH
          }
          else {
            if (placeholderCountHolder.count == finalArgumentCount) ResultType.SUCCESS else ResultType.PLACE_HOLDER_MISMATCH
          }
        }
        PlaceholderLoggerType.SLF4J_EQUAL_PLACEHOLDERS, PlaceholderLoggerType.LOG4J_EQUAL_PLACEHOLDERS, PlaceholderLoggerType.AKKA_PLACEHOLDERS -> {
          if (placeholderCountHolder.status == PlaceholdersStatus.PARTIAL) {
            if (placeholderCountHolder.count <= finalArgumentCount) ResultType.SUCCESS else ResultType.PARTIAL_PLACE_HOLDER_MISMATCH
          }
          else {
            if (placeholderCountHolder.count == finalArgumentCount) ResultType.SUCCESS else ResultType.PLACE_HOLDER_MISMATCH
          }
        }
        PlaceholderLoggerType.LOG4J_OLD_STYLE,
        PlaceholderLoggerType.LOG4J_FORMATTED_STYLE -> { // if there is more than one argument and the last argument is an exception, but there is a placeholder for
          // the exception, then the stack trace won't be logged.
          val type: ResultType
          if (placeholderCountHolder.status == PlaceholdersStatus.PARTIAL) {
            type = if ((placeholderCountHolder.count <= finalArgumentCount && (!context.lastArgumentIsException || finalArgumentCount > 1)) || (context.lastArgumentIsException && placeholderCountHolder.count <= finalArgumentCount - 1) || //consider the most general case
                       (context.lastArgumentIsSupplier && (placeholderCountHolder.count <= finalArgumentCount))) ResultType.SUCCESS
            else ResultType.PARTIAL_PLACE_HOLDER_MISMATCH
          }
          else {
            type = if ((placeholderCountHolder.count == finalArgumentCount && (!context.lastArgumentIsException || finalArgumentCount > 1)) || (context.lastArgumentIsException && placeholderCountHolder.count == finalArgumentCount - 1) || //consider the most general case
                       (context.lastArgumentIsSupplier && (placeholderCountHolder.count == finalArgumentCount || placeholderCountHolder.count == finalArgumentCount - 1))) ResultType.SUCCESS
            else ResultType.PLACE_HOLDER_MISMATCH
          }
          finalArgumentCount = if (context.lastArgumentIsException) finalArgumentCount - 1 else finalArgumentCount
          type
        }
      }

      if (resultType == ResultType.SUCCESS) {
        return true
      }

      registerProblem(holder, context.logStringArgument, Result(finalArgumentCount, placeholderCountHolder.count, resultType))

      return true
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
  }

  enum class Slf4jToLog4J2Type {
    AUTO, YES, NO
  }

  private data class Result(val argumentCount: Int, val placeholderCount: Int, val result: ResultType)
}
