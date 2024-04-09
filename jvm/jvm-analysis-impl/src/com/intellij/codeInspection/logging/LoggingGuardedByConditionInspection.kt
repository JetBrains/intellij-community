// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.logging

import com.intellij.analysis.JvmAnalysisBundle
import com.intellij.codeInspection.AbstractBaseUastLocalInspectionTool
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.options.OptPane
import com.intellij.codeInspection.registerUProblem
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandQuickFix
import com.intellij.openapi.project.Project
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.uast.UastHintedVisitorAdapter
import org.jetbrains.uast.*
import org.jetbrains.uast.generate.UastCodeGenerationPlugin
import org.jetbrains.uast.generate.replace
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor
import org.jetbrains.uast.visitor.AbstractUastVisitor

class LoggingGuardedByConditionInspection : AbstractBaseUastLocalInspectionTool() {

  @JvmField
  var showOnlyIfFixPossible: Boolean = true

  override fun getOptionsPane(): OptPane {
    return OptPane.pane(
      OptPane.checkbox("showOnlyIfFixPossible",
                       JvmAnalysisBundle.message("jvm.inspection.log.guarded.warn.if.fix.possible"))
    )
  }


  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor =
    UastHintedVisitorAdapter.create(
      holder.file.language,
      LoggingGuardedByLogConditionVisitor(holder, isOnTheFly),
      arrayOf(UIfExpression::class.java),
      directOnly = true
    )

  inner class LoggingGuardedByLogConditionVisitor(
    private val holder: ProblemsHolder,
    private val isOnTheFly: Boolean,
  ) : AbstractUastNonRecursiveVisitor() {

    override fun visitIfExpression(node: UIfExpression): Boolean {
      val condition = node.condition
      val visitor = object : AbstractUastVisitor() {
        val conditions = mutableListOf<UCallExpression>()
        override fun visitCallExpression(node: UCallExpression): Boolean {
          val guardedCondition = LoggingUtil.getGuardedCondition(node)
          if (guardedCondition == null) {
            conditions.add(node)
          }
          return true
        }
      }
      condition.accept(visitor)
      if (visitor.conditions.size != 1) return true
      val guardedCondition = visitor.conditions[0]

      val uIfExpression = guardedCondition.getParentOfType<UIfExpression>() ?: return true
      if (node.sourcePsi != uIfExpression.sourcePsi) return true
      val levelFromCondition = LoggingUtil.getLevelFromCondition(guardedCondition) ?: return true
      val calls: List<UCallExpression> = LoggingUtil.getLoggerCalls(guardedCondition)
      if (calls.isEmpty()) return true
      val uCallExpression = calls.find {
        val currentLoggerLevel = LoggingUtil.getLoggerLevel(it) ?: return@find false
        LoggingUtil.isGuardedIn(levelFromCondition, currentLoggerLevel) &&
        guardedCondition.receiver?.sourcePsi?.text == it.receiver?.sourcePsi?.text &&
        LoggingUtil.LOG_MATCHERS_WITHOUT_BUILDERS.uCallMatches(it)
      }
      if (uCallExpression == null) return true

      val fixPossible = checkIfFixPossible(guardedCondition, uCallExpression, calls)
      if (!fixPossible && showOnlyIfFixPossible) return true
      val sourcePsiGuardedCondition = guardedCondition.sourcePsi ?: return true
      val fixes = if (fixPossible) arrayOf(UnguardedFix()) else arrayOf()
      val isInformationLevel = isOnTheFly && InspectionProjectProfileManager.isInformationLevel(getShortName(), sourcePsiGuardedCondition)

      val toHighlight = if (isInformationLevel) node else guardedCondition

      val message = JvmAnalysisBundle.message("jvm.inspection.log.guarded.problem.descriptor")
      holder.registerUProblem(toHighlight, message, *fixes)
      return true
    }

    private fun checkIfFixPossible(guardedCondition: UExpression, node: UCallExpression, calls: List<UCallExpression>): Boolean {
      val uIfExpression = guardedCondition.getParentOfType<UIfExpression>() ?: return false
      if (!(uIfExpression.condition.sourcePsi == guardedCondition.sourcePsi ||
            (guardedCondition.uastParent is UQualifiedReferenceExpression &&
             uIfExpression.condition.sourcePsi == guardedCondition.uastParent?.sourcePsi) ||
            (guardedCondition.uastParent is USimpleNameReferenceExpression &&
             guardedCondition.uastParent?.uastParent is UQualifiedReferenceExpression &&
             uIfExpression.condition.sourcePsi == guardedCondition.uastParent?.uastParent?.sourcePsi)
           )
      ) {
        return false
      }
      val thenExpression = uIfExpression.thenExpression
      if (thenExpression?.sourcePsi != node.sourcePsi &&
          !(thenExpression is UQualifiedReferenceExpression &&
           node.uastParent?.sourcePsi == thenExpression.sourcePsi)) {
        if (thenExpression !is UBlockExpression) {
          return false
        }
        val nestedExpressions = thenExpression.expressions
        if (nestedExpressions.size != calls.size) {
          return false
        }
        if (nestedExpressions.zip(calls).any { (nested, call) ->
            nested.sourcePsi != call.sourcePsi &&
            (call.uastParent is UQualifiedReferenceExpression && call.uastParent?.sourcePsi != nested.sourcePsi)
          }) {
          return false
        }
      }
      return true
    }
  }

  private inner class UnguardedFix : PsiUpdateModCommandQuickFix() {

    override fun getFamilyName(): String {
      return JvmAnalysisBundle.message("jvm.inspection.log.guarded.fix.family.name")
    }

    override fun applyFix(project: Project, element: PsiElement, updater: ModPsiUpdater) {
      val uIfExpression: UIfExpression = element.toUElement()?.getParentOfType<UIfExpression>(strict = false) ?: return
      val thenExpression = uIfExpression.thenExpression
      if (thenExpression !is UExpression) return
      if (thenExpression !is UBlockExpression) {
        uIfExpression.replace(thenExpression)
        return
      }


      val ifStatementSourcePsi = uIfExpression.sourcePsi ?: return

      val uastCodeGenerationPlugin = UastCodeGenerationPlugin.byLanguage(ifStatementSourcePsi.language)

      val commentSaver = uastCodeGenerationPlugin?.grabComments(uIfExpression)

      val expressions = thenExpression.expressions
      if (expressions.isEmpty()) return
      var currentParent = ifStatementSourcePsi.parent
      var after = ifStatementSourcePsi
      var nextExpression = expressions[0].sourcePsi
      val lastExpression = expressions.last().sourcePsi ?: return
      while (nextExpression?.parent.toUElement()?.sourcePsi == expressions[0].sourcePsi) {
        nextExpression = nextExpression?.parent
      }
      if (nextExpression == null) return
      while (true) {
        if (nextExpression == null) break
        commentSaver?.markUnchanged(nextExpression.toUElement())
        var newAdded: PsiElement = currentParent.addAfter(nextExpression.copy(), after)
        if (nextExpression is PsiWhiteSpace) {
          while (newAdded.nextSibling !is PsiWhiteSpace) {
            newAdded = newAdded.parent ?: break
          }
          after = newAdded.nextSibling
        }
        else {
          after = newAdded
        }
        currentParent = newAdded.parent
        if (PsiTreeUtil.isAncestor(nextExpression, lastExpression, false)) break
        nextExpression = nextExpression.nextSibling ?: break
      }
      ifStatementSourcePsi.delete()
      val uElement = after.toUElement()
      if (uElement != null) {
        commentSaver?.restore(uElement)
      }
    }
  }
}