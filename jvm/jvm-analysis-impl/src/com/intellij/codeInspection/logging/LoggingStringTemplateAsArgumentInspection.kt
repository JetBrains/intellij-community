// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.logging

import com.intellij.analysis.JvmAnalysisBundle
import com.intellij.codeInspection.*
import com.intellij.openapi.project.Project
import com.intellij.psi.CommonClassNames
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiType
import com.intellij.uast.UastHintedVisitorAdapter
import org.jetbrains.uast.*
import org.jetbrains.uast.expressions.UInjectionHost
import org.jetbrains.uast.generate.getUastElementFactory
import org.jetbrains.uast.generate.replace
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

class LoggingStringTemplateAsArgumentInspection : AbstractBaseUastLocalInspectionTool() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor =
    UastHintedVisitorAdapter.create(
      holder.file.language,
      LoggingStringTemplateAsArgumentVisitor(holder),
      arrayOf(UCallExpression::class.java),
      directOnly = true
    )
}

private class LoggingStringTemplateAsArgumentVisitor(
  private val holder: ProblemsHolder,
) : AbstractUastNonRecursiveVisitor() {

  override fun visitCallExpression(node: UCallExpression): Boolean {
    if (!LOG_MATCHERS.uCallMatches(node)) return true
    val valueArguments = node.valueArguments
    if (valueArguments.isEmpty()) return true
    var stringExpression = valueArguments[0]
    var indexStringExpression = 0
    if (!canBePattern(stringExpression.getExpressionType())) {
      if (valueArguments.size < 2) {
        return true
      }
      stringExpression = valueArguments[1]
      indexStringExpression = 1
      if (!canBePattern(stringExpression.getExpressionType())) {
        return true
      }
    }

    if (!isPattern(stringExpression)) {
      return true
    }


    val message = JvmAnalysisBundle.message("jvm.inspection.logging.string.template.as.argument.problem.descriptor")
    holder.registerUProblem(node, message, ConvertToPlaceHolderQuickfix(indexStringExpression))
    return true
  }

  private fun isPattern(stringExpression: UExpression): Boolean {
    //Perhaps, it needs to be customized for Java Pattern in the future
    return stringExpression is UPolyadicExpression && stringExpression is UInjectionHost &&
           !stringExpression.operands.all { it is ULiteralExpression }
  }

  private fun canBePattern(expressionType: PsiType?): Boolean {
    if (
      expressionType?.equalsToText(CommonClassNames.JAVA_LANG_STRING) == true ||
      expressionType?.equalsToText(CommonClassNames.JAVA_LANG_CHAR_SEQUENCE) == true
    ) return true
    return false
  }
}

class ConvertToPlaceHolderQuickfix(private val indexStringExpression: Int) : LocalQuickFix {

  override fun getFamilyName(): String = JvmAnalysisBundle.message("jvm.inspection.logging.string.template.as.argument.quickfix.name")
  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val uCallExpression = descriptor.psiElement.getUastParentOfType<UCallExpression>() ?: return
    val parametersBeforeString: MutableList<UExpression> = mutableListOf()
    val parametersAfterString: MutableList<UExpression> = mutableListOf()
    val valueArguments = uCallExpression.valueArguments
    if (indexStringExpression == 1) {
      parametersBeforeString.add(valueArguments[0])
    }
    val builderString = StringBuilder()
    val template = valueArguments[indexStringExpression] as? UPolyadicExpression ?: return
    var indexOuterPlaceholder = indexStringExpression + 1
    val loggerType = getLoggerType(uCallExpression)

    for (operand in template.operands) {
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
        builderString.append(text)
      }
      else {
        if (builderString.endsWith("\\") && (loggerType == LoggerType.SLF4J_LOGGER_TYPE || loggerType == LoggerType.SLF4J_BUILDER_TYPE)) {
          builderString.append("\\")
        }
        builderString.append("{}")
        parametersAfterString.add(operand)
      }
    }

    if (indexOuterPlaceholder < valueArguments.size) {
      for (index in indexOuterPlaceholder until valueArguments.size) {
        parametersAfterString.add(valueArguments[index])
      }
    }
    val elementFactory = uCallExpression.getUastElementFactory(project) ?: return
    val newText = elementFactory.createStringLiteralExpression(builderString.toString(), uCallExpression.sourcePsi) ?: return
    val newParameters = mutableListOf<UExpression>()
    newParameters.addAll(parametersBeforeString)
    newParameters.add(newText)
    newParameters.addAll(parametersAfterString)

    val methodName = uCallExpression.methodName ?: return
    val newCall = elementFactory.createCallExpression(uCallExpression.receiver, methodName, newParameters, uCallExpression.returnType,
                                                      uCallExpression.kind, uCallExpression.sourcePsi
    ) ?: return
    val oldCall = uCallExpression.getQualifiedParentOrThis()
    oldCall.replace(newCall)
  }
}