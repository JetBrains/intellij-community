// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.logging

import com.intellij.analysis.JvmAnalysisBundle
import com.intellij.codeInspection.AbstractBaseUastLocalInspectionTool
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.registerUProblem
import com.intellij.psi.PsiElementVisitor
import com.intellij.uast.UastHintedVisitorAdapter
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

class LoggingConditionDisagreesWithLogLevelStatementInspection : AbstractBaseUastLocalInspectionTool() {

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
      if (LoggingUtil.LOG_MATCHERS.uCallMatches(node)) {
        processActualLoggers(node)
      }
      else if (LoggingUtil.LEGACY_LOG_MATCHERS.uCallMatches(node)) {
        processLegacyLoggers(node)
      }
      return true
    }

    private fun processActualLoggers(call: UCallExpression) {
      val guardedCondition = LoggingUtil.getGuardedCondition(call) ?: return
      val loggerLevel = LoggingUtil.getLoggerLevel(call) ?: return
      val levelFromCondition = LoggingUtil.getLevelFromCondition(guardedCondition) ?: return
      if (!LoggingUtil.isGuardedIn(levelFromCondition, loggerLevel)) {
        registerProblem(guardedCondition, levelFromCondition.name, loggerLevel.name)
      }
    }

    private fun processLegacyLoggers(call: UCallExpression) {
      val guardedCondition = LoggingUtil.getGuardedCondition(call) ?: return
      val loggerLevel = LoggingUtil.getLegacyLoggerLevel(call) ?: return
      val levelFromCondition = LoggingUtil.getLegacyLevelFromCondition(guardedCondition) ?: return
      if (!LoggingUtil.isLegacyGuardedIn(levelFromCondition, loggerLevel)) {
        registerProblem(guardedCondition, levelFromCondition.name, loggerLevel.name)
      }
    }

    private fun registerProblem(call: UExpression,
                                levelFromCondition: String,
                                loggerLevel: String) {
      val message = JvmAnalysisBundle.message("jvm.inspection.logging.condition.disagrees.with.log.statement.problem.descriptor",
                                              levelFromCondition, loggerLevel)
      if (call is UCallExpression) {
        holder.registerUProblem(call, message)
      }
      if (call is UReferenceExpression) {
        holder.registerUProblem(call, message)
      }
    }
  }
}