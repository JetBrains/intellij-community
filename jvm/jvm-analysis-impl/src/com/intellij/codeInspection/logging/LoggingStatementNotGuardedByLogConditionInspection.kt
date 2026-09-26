// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.logging

import com.intellij.analysis.JvmAnalysisBundle
import com.intellij.codeInspection.AbstractBaseUastLocalInspectionTool
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.logging.LoggingUtil.LimitLevelType
import com.intellij.codeInspection.options.OptPane
import com.intellij.codeInspection.registerUProblem
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandQuickFix
import com.intellij.openapi.project.Project
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiTypes
import com.intellij.psi.impl.LanguageConstantExpressionEvaluator
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.uast.UastHintedVisitorAdapter
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UPolyadicExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.UastCallKind
import org.jetbrains.uast.generate.getUastElementFactory
import org.jetbrains.uast.generate.replace
import org.jetbrains.uast.getUCallExpression
import org.jetbrains.uast.resolveToUElement
import org.jetbrains.uast.skipParenthesizedExprDown
import org.jetbrains.uast.toUElement
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

class LoggingStatementNotGuardedByLogConditionInspection : AbstractBaseUastLocalInspectionTool() {

  @JvmField
  var myLimitLevelType: LimitLevelType = LimitLevelType.DEBUG_AND_LOWER

  @JvmField
  var flagUnguardedConstant: Boolean = false

  override fun getOptionsPane(): OptPane {
    return OptPane.pane(
      OptPane.dropdown(
        "myLimitLevelType",
        JvmAnalysisBundle.message("jvm.inspection.log.statement.not.guarded.warn.on.label"),
        OptPane.option(LimitLevelType.ALL,
                       JvmAnalysisBundle.message("jvm.inspection.log.statement.not.guarded.all.levels.option")),
        OptPane.option(LimitLevelType.WARN_AND_LOWER,
                       JvmAnalysisBundle.message("jvm.inspection.log.statement.not.guarded.warn.level.and.lower.option")),
        OptPane.option(LimitLevelType.INFO_AND_LOWER,
                       JvmAnalysisBundle.message("jvm.inspection.log.statement.not.guarded.info.level.and.lower.option")),
        OptPane.option(LimitLevelType.DEBUG_AND_LOWER,
                       JvmAnalysisBundle.message("jvm.inspection.log.statement.not.guarded.debug.level.and.lower.option")),
        OptPane.option(LimitLevelType.TRACE,
                       JvmAnalysisBundle.message("jvm.inspection.log.statement.not.guarded.trace.level.option")),
      ),
      OptPane.checkbox("flagUnguardedConstant", JvmAnalysisBundle.message("jvm.inspection.log.statement.not.guarded.unguarded.constant.option"))
        .description(JvmAnalysisBundle.message("jvm.inspection.log.statement.not.guarded.unguarded.constant.option.comment")),
      )
  }


  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor =
    UastHintedVisitorAdapter.create(
      holder.file.language,
      LogStatementNotGuardedByLogConditionVisitor(holder, isOnTheFly),
      arrayOf(UCallExpression::class.java),
      directOnly = true
    )

  inner class LogStatementNotGuardedByLogConditionVisitor(
    private val holder: ProblemsHolder,
    private val isOnTheFly: Boolean,
  ) : AbstractUastNonRecursiveVisitor() {
    override fun visitCallExpression(node: UCallExpression): Boolean {
      val sourcePsi = node.sourcePsi ?: return true
      if (!LoggingUtil.LOG_MATCHERS_WITHOUT_BUILDERS.uCallMatches(node)) {
        return true
      }

      val isInformationLevel = isOnTheFly && InspectionProjectProfileManager.isInformationLevel(getShortName(), sourcePsi)
      if (!isInformationLevel && LoggingUtil.skipAccordingLevel(node, myLimitLevelType)) {
        return true
      }

      if (isSurroundedByLogGuard(node)) {
        return true
      }

      if (!isInformationLevel && skipIfOnlyConstantArguments(node)) return true

      val nodeParent = node.uastParent
      val qualifiedExpression = if (nodeParent is UQualifiedReferenceExpression) nodeParent else node
      var psiElement = qualifiedExpression.sourcePsi
      while (psiElement?.parent.toUElement() == qualifiedExpression) {
        psiElement = psiElement?.parent
      }
      val before = PsiTreeUtil.skipWhitespacesAndCommentsBackward(psiElement).toUElement() as? UQualifiedReferenceExpression
      val beforeCall = before.getUCallExpression(2)
      val beforeLoggerLevel = LoggingUtil.getLoggerLevel(beforeCall)
      if (beforeCall != null &&
          LoggingUtil.LOG_MATCHERS_WITHOUT_BUILDERS.uCallMatches(beforeCall)
      ) {
        val receiverText = node.receiver?.sourcePsi?.text
        val loggerLevel = LoggingUtil.getLoggerLevel(node)
        if (receiverText != null && beforeCall.receiver?.sourcePsi?.text == receiverText &&
            !(skipIfOnlyConstantArguments(beforeCall)) &&
            beforeCall.methodName == node.methodName &&
            beforeLoggerLevel == loggerLevel) {
          return true
        }
      }

      var parent = nodeParent
      if (parent is UQualifiedReferenceExpression) {
        parent = parent.uastParent
      }

      if (parent is ULambdaExpression || parent is UReturnExpression) {
        return true
      }

      val message = JvmAnalysisBundle.message("jvm.inspection.log.statement.not.guarded.log.problem.descriptor")

      if (nodeParent is UQualifiedReferenceExpression) {
        holder.registerUProblem(nodeParent as UExpression, message, CreateGuardFix())
      }
      else {
        holder.registerUProblem(node, message, CreateGuardFix())
      }
      return true
    }

    private fun skipIfOnlyConstantArguments(node: UCallExpression): Boolean {
      val arguments = node.valueArguments
      if (arguments.isEmpty()) {
        return true
      }

      val expressionEvaluator = LanguageConstantExpressionEvaluator.INSTANCE.forLanguage(node.lang)

      if (expressionEvaluator != null && !flagUnguardedConstant) {
        var constant = true
        for (parenArgument in arguments) {
          val argument = parenArgument.skipParenthesizedExprDown()
          if (argument is ULambdaExpression) {
            continue
          }

          val sourcePsi = argument.sourcePsi
          if (isConstantOrPolyadicWithConstants(argument)) {
            continue
          }
          if (sourcePsi != null && expressionEvaluator.computeConstantExpression(sourcePsi, false) == null) {
            constant = false
            break
          }
        }
        if (constant) {
          return true
        }
      }
      return false
    }

    private fun isConstantOrPolyadicWithConstants(argument: UExpression): Boolean {
      if (argument is ULiteralExpression) return true
      if (argument is UBinaryExpression && argument.operands.all { isConstantOrPolyadicWithConstants(it) }) return true
      if (argument is UPolyadicExpression && argument.operands.all { isConstantOrPolyadicWithConstants(it) }) return true
      return false
    }

    private fun isSurroundedByLogGuard(callExpression: UCallExpression): Boolean {
      val guardedCondition = LoggingUtil.getGuardedCondition(callExpression) ?: return false
      val loggerLevel = LoggingUtil.getLoggerLevel(callExpression) ?: return true
      val levelFromCondition = LoggingUtil.getLevelFromCondition(guardedCondition) ?: return true
      return LoggingUtil.isGuardedIn(levelFromCondition, loggerLevel)
    }
  }

  private class CreateGuardFix : PsiUpdateModCommandQuickFix() {

    override fun getFamilyName(): String {
      return JvmAnalysisBundle.message("jvm.inspection.log.statement.not.guarded.log.fix.family.name")
    }

    override fun applyFix(project: Project, element: PsiElement, updater: ModPsiUpdater) {
      val uCallExpression: UCallExpression = element.toUElement().getUCallExpression(2) ?: return

      val qualifiedExpression = if (uCallExpression.uastParent is UQualifiedReferenceExpression) uCallExpression.uastParent else uCallExpression
      if (qualifiedExpression !is UExpression) return

      var currentElement = qualifiedExpression.sourcePsi
      while (currentElement?.parent.toUElement() == qualifiedExpression) {
        currentElement = currentElement?.parent
      }

      if (currentElement == null) return

      val calls = mutableListOf(qualifiedExpression)
      val receiverText = uCallExpression.receiver?.sourcePsi?.text ?: return

      val loggerLevel = LoggingUtil.getLoggerLevel(uCallExpression) ?: return

      while (true) {
        val nextElement = PsiTreeUtil.skipWhitespacesAndCommentsForward(currentElement)
        val after = nextElement.toUElement() as? UQualifiedReferenceExpression
        val afterCall = after.getUCallExpression(2) ?: break
        val afterLevel = LoggingUtil.getLoggerLevel(afterCall)
        if (after != null &&
            (LoggingUtil.LOG_MATCHERS_WITHOUT_BUILDERS.uCallMatches(afterCall)) &&
            receiverText == after.receiver.sourcePsi?.text &&
            uCallExpression.methodName == afterCall.methodName &&
            afterLevel == loggerLevel) {
          calls.add(after)
          currentElement = nextElement
          continue
        }
        break
      }
      val conditionCall = LoggingUtil.GUARD_MAP.entries.firstOrNull { it.value == loggerLevel.name.lowercase() }?.key ?: return

      val elementFactory = uCallExpression.getUastElementFactory(project) ?: return
      if (calls.isEmpty()) return
      var condition: UExpression =
        elementFactory.createQualifiedReference(qualifiedName = "${receiverText}.$conditionCall",
                                                context = uCallExpression.sourcePsi) ?: return

      if (condition.uastParent is UQualifiedReferenceExpression) {
        condition = condition.uastParent as UQualifiedReferenceExpression
      }
      val blockExpression = elementFactory.createBlockExpression(calls, uCallExpression.sourcePsi) ?: return

      val ifExpression = elementFactory.createIfExpression(condition, blockExpression, null, element) ?: return

      val uExpression = calls[0]
      val newIfExpression = uExpression.replace(ifExpression)
      val newCondition = newIfExpression?.condition
      if (newCondition is UQualifiedReferenceExpression && newCondition.resolveToUElement() !is UMethod) {
        val callCondition = elementFactory.createCallExpression(newCondition.receiver, conditionCall, emptyList(), PsiTypes.booleanType(), UastCallKind.METHOD_CALL, newCondition.sourcePsi)
        if (callCondition != null) {
          newCondition.replace(callCondition)
        }
      }

      if (calls.size > 1) {
        for (i in 1 until calls.size) {
          calls[i].sourcePsi?.delete()
        }
      }
    }
  }
}