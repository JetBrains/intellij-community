// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.logging

import com.intellij.analysis.JvmAnalysisBundle
import com.intellij.codeInspection.*
import com.intellij.codeInspection.logging.LoggingUtil.Companion
import com.intellij.codeInspection.logging.LoggingUtil.Companion.LOG_MATCHERS
import com.intellij.codeInspection.logging.LoggingUtil.Companion.countPlaceHolders
import com.intellij.codeInspection.logging.LoggingUtil.Companion.getLoggerLevel
import com.intellij.codeInspection.logging.LoggingUtil.Companion.getLoggerType
import com.intellij.codeInspection.logging.LoggingUtil.Companion.isGuarded
import com.intellij.codeInspection.options.OptPane
import com.intellij.lang.Language
import com.intellij.openapi.project.Project
import com.intellij.psi.CommonClassNames
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiType
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.TypeConversionUtil
import com.intellij.uast.UastHintedVisitorAdapter
import org.jetbrains.uast.*
import org.jetbrains.uast.expressions.UInjectionHost
import org.jetbrains.uast.generate.getUastElementFactory
import org.jetbrains.uast.generate.replace
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

class LoggingStringTemplateAsArgumentInspection : AbstractBaseUastLocalInspectionTool() {

  @JvmField
  var myLimitLevelType: LimitLevelType = LimitLevelType.DEBUG_AND_LOWER

  @JvmField
  var mySkipPrimitives: Boolean = true

  enum class LimitLevelType {
    ALL, WARN_AND_LOWER, INFO_AND_LOWER, DEBUG_AND_LOWER, TRACE
  }

  override fun getOptionsPane(): OptPane {
    return OptPane.pane(
      OptPane.dropdown(
        "myLimitLevelType",
        JvmAnalysisBundle.message("jvm.inspection.logging.string.template.as.argument.warn.on.label"),
        OptPane.option(LimitLevelType.ALL,
                       JvmAnalysisBundle.message("jvm.inspection.logging.string.template.as.argument.all.levels.option")),
        OptPane.option(LimitLevelType.WARN_AND_LOWER,
                       JvmAnalysisBundle.message("jvm.inspection.logging.string.template.as.argument.warn.level.and.lower.option")),
        OptPane.option(LimitLevelType.INFO_AND_LOWER,
                       JvmAnalysisBundle.message("jvm.inspection.logging.string.template.as.argument.info.level.and.lower.option")),
        OptPane.option(LimitLevelType.DEBUG_AND_LOWER,
                       JvmAnalysisBundle.message("jvm.inspection.logging.string.template.as.argument.debug.level.and.lower.option")),
        OptPane.option(LimitLevelType.TRACE,
                       JvmAnalysisBundle.message("jvm.inspection.logging.string.template.as.argument.trace.level.option")),
      ),
      OptPane.checkbox("mySkipPrimitives",
                       JvmAnalysisBundle.message("jvm.inspection.logging.string.template.as.argument.skip.on.primitives"))
    )
  }

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor =
    UastHintedVisitorAdapter.create(
      holder.file.language,
      LoggingStringTemplateAsArgumentVisitor(holder),
      arrayOf(UCallExpression::class.java),
      directOnly = true
    )

  inner class LoggingStringTemplateAsArgumentVisitor(
    private val holder: ProblemsHolder,
  ) : AbstractUastNonRecursiveVisitor() {

    override fun visitCallExpression(node: UCallExpression): Boolean {
      if (!LOG_MATCHERS.uCallMatches(node)) return true
      if (skipAccordingLevel(node)) return true
      val valueArguments = node.valueArguments
      val uMethod = node.resolve().toUElement() as? UMethod ?: return true
      val uastParameters = uMethod.uastParameters
      if (valueArguments.isEmpty() || uastParameters.isEmpty()) return true
      var indexStringExpression = 0
      if (!uastParameters[indexStringExpression].type.canBeText()) {
        if (valueArguments.size < 2 || uastParameters.size < 2) {
          return true
        }
        indexStringExpression = 1
        if (!uastParameters[indexStringExpression].type.canBeText()) {
          return true
        }
      }

      val stringExpression = valueArguments[indexStringExpression]

      val parts: MutableList<UExpression> = mutableListOf()
      //if parameter is String and argument is NOT String, probably it is String Template like "$object" for Kotlin
      if (stringExpression !is UPolyadicExpression && !stringExpression.getExpressionType().canBeText() &&
          stringExpression.lang == Language.findLanguageByID("kotlin")) {
        val text: String = stringExpression.sourcePsi?.parent?.text ?: return true
        if (text.startsWith("$")) {
          parts.add(stringExpression)
        }
      }

      if (stringExpression is UPolyadicExpression && hasPattern(stringExpression)) {
        if (isPattern(stringExpression)) {
          parts.addAll(stringExpression.operands)
        }
        else {
          parts.addAll(stringExpression.operands.flatMap { operand->
            if (isPattern(operand) && operand is UPolyadicExpression) {
              operand.operands
            }
            else {
              listOf(operand)
            }
          })
        }
      }

      if (parts.isEmpty()) {
        return true
      }

      //strange behavior for last parameter as exception. let's ignore this case
      val injected = parts.filter { it !is ULiteralExpression }
      if ((injected.size == 1 &&
           InheritanceUtil.isInheritor(injected.first().getExpressionType(), CommonClassNames.JAVA_LANG_THROWABLE)) ||
          ((valueArguments.lastIndex - indexStringExpression) == 1 &&
          InheritanceUtil.isInheritor(valueArguments.last().getExpressionType(), CommonClassNames.JAVA_LANG_THROWABLE))
        ) {
        return true
      }

      if (mySkipPrimitives && allExpressionsInPatternArePrimitivesOrWrappers(parts)) {
        return true
      }

      if (isGuarded(node)) {
        return true
      }

      val message = JvmAnalysisBundle.message("jvm.inspection.logging.string.template.as.argument.problem.descriptor")
      holder.registerUProblem(node, message, ConvertToPlaceHolderQuickfix(indexStringExpression))
      return true
    }

    private fun allExpressionsInPatternArePrimitivesOrWrappers(operands: List<UExpression>): Boolean {
      return operands.all { it.getExpressionType().isPrimitiveOrWrappers() }
    }

    private fun skipAccordingLevel(node: UCallExpression): Boolean {
      if (myLimitLevelType != LimitLevelType.ALL) {
        val loggerLevel = getLoggerLevel(node)
        if (loggerLevel == null) return true
        val notSkip: Boolean = when (loggerLevel) {
          Companion.LevelType.FATAL -> false
          Companion.LevelType.ERROR -> false
          Companion.LevelType.WARN -> myLimitLevelType.ordinal == LimitLevelType.WARN_AND_LOWER.ordinal
          Companion.LevelType.INFO -> myLimitLevelType.ordinal <= LimitLevelType.INFO_AND_LOWER.ordinal
          Companion.LevelType.DEBUG -> myLimitLevelType.ordinal <= LimitLevelType.DEBUG_AND_LOWER.ordinal
          Companion.LevelType.TRACE -> myLimitLevelType.ordinal <= LimitLevelType.TRACE.ordinal
        }
        return !notSkip
      }
      else {
        return false
      }
    }

    /**
     * @param stringExpression The string expression to check.
     * @return True if the string expression consists of patterns or string only, false otherwise.
     */
    private fun hasPattern(stringExpression: UPolyadicExpression): Boolean {
      //it needs to be customized for Java
      if (isPattern(stringExpression)) return true

      if(stringExpression.operands
           .any { operand -> operand is ULiteralExpression &&
                             !operand.getExpressionType().canBeText()}) return false
      return stringExpression.operands.any { operand-> isPattern(operand) }
    }
  }
}

private fun isPattern(stringExpression: UExpression): Boolean {
  return stringExpression is UPolyadicExpression &&
         stringExpression is UInjectionHost &&
         !stringExpression.operands.all { it is ULiteralExpression }
}

private fun PsiType?.canBeText(): Boolean {
  return this?.equalsToText(CommonClassNames.JAVA_LANG_STRING) == true ||
         this?.equalsToText(CommonClassNames.JAVA_LANG_CHAR_SEQUENCE) == true
}

private fun PsiType?.isPrimitiveOrWrappers(): Boolean {
  return this != null && (TypeConversionUtil.isPrimitiveAndNotNull(this) ||
                          TypeConversionUtil.isPrimitiveWrapper(this) ||
                          canBeText())
}

private class ConvertToPlaceHolderQuickfix(private val indexStringExpression: Int) : LocalQuickFix {

  override fun getFamilyName(): String = JvmAnalysisBundle.message("jvm.inspection.logging.string.template.as.argument.quickfix.name")
  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val uCallExpression = descriptor.psiElement.getUastParentOfType<UCallExpression>() ?: return
    val (parametersBeforeString: MutableList<UExpression>, parametersAfterString: MutableList<UExpression>, textPattern) =
      createMethodContext(uCallExpression)

    val elementFactory = uCallExpression.getUastElementFactory(project) ?: return
    val newText = elementFactory.createStringLiteralExpression(textPattern.toString(), uCallExpression.sourcePsi) ?: return
    val newParameters = mutableListOf<UExpression>().apply {
      addAll(parametersBeforeString)
      add(newText)
      addAll(parametersAfterString)
    }

    val methodName = uCallExpression.methodName ?: return
    val newCall = elementFactory.createCallExpression(uCallExpression.receiver, methodName, newParameters, uCallExpression.returnType,
                                                      uCallExpression.kind, uCallExpression.sourcePsi
    ) ?: return
    val oldCall = uCallExpression.getQualifiedParentOrThis()
    oldCall.replace(newCall)
  }

  private fun createMethodContext(uCallExpression: UCallExpression): MethodContext {
    val parametersBeforeString: MutableList<UExpression> = mutableListOf()
    val parametersAfterString: MutableList<UExpression> = mutableListOf()
    val valueArguments = uCallExpression.valueArguments
    if (indexStringExpression == 1) {
      parametersBeforeString.add(valueArguments[0])
    }
    val argument = valueArguments[indexStringExpression]

    val textPattern = StringBuilder()
    var indexOuterPlaceholder = indexStringExpression + 1
    if (argument is UPolyadicExpression) {
      val operands = flatPatterns(argument)
      val loggerType = getLoggerType(uCallExpression)
      for (operand in operands) {
        if (operand is ULiteralExpression && operand.isString) {
          val text = operand.value.toString()
          val countPlaceHolders = countPlaceHolders(text, loggerType)
          for (index in 0 until countPlaceHolders) {
            val nextIndex = indexOuterPlaceholder + index
            if (nextIndex < valueArguments.size) {
              parametersAfterString.add(valueArguments[nextIndex])
            }
            else {
              break
            }
          }
          indexOuterPlaceholder += countPlaceHolders
          textPattern.append(text)
        }
        else {
          if (textPattern.endsWith("\\") &&
              (loggerType == Companion.LoggerType.SLF4J_LOGGER_TYPE || loggerType == Companion.LoggerType.SLF4J_BUILDER_TYPE)) {
            textPattern.append("\\")
          }
          textPattern.append("{}")
          parametersAfterString.add(operand)
        }
      }
    }
    else {
      textPattern.append("{}")
      parametersAfterString.add(argument)
    }

    if (indexOuterPlaceholder < valueArguments.size) {
      for (index in indexOuterPlaceholder until valueArguments.size) {
        parametersAfterString.add(valueArguments[index])
      }
    }
    return MethodContext(parametersBeforeString, parametersAfterString, textPattern)
  }


  private fun flatPatterns(polyadicExpression: UPolyadicExpression): List<UExpression> {
    if (isPattern(polyadicExpression)) {
      return polyadicExpression.operands
    }
    val result = polyadicExpression.operands
      .flatMap { operand ->
        if (operand is UPolyadicExpression) {
          flatPatterns(operand)
        }
        else {
          listOf(operand)
        }
      }
    return result
  }

  data class MethodContext(val parametersBeforeString: MutableList<UExpression>,
                           val parametersAfterString: MutableList<UExpression>,
                           val textPattern: StringBuilder)
}