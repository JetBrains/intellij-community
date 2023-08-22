// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.logging

import com.intellij.analysis.JvmAnalysisBundle
import com.intellij.codeInspection.AbstractBaseUastLocalInspectionTool
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.registerUProblem
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.uast.UastHintedVisitorAdapter
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.toUElementOfType
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
      //unfortunately it is impossible to use guarded as a start point, because it can be really complex
      if (LoggingUtil.LOG_MATCHERS.uCallMatches(node)) {
        processActualLoggers(node)
      }
      else if (LoggingUtil.LEGACY_LOG_MATCHERS.uCallMatches(node)) {
        processLegacyLoggers(node)
      }
      return true
    }

    private fun processActualLoggers(callExpression: UCallExpression) {
      val guardedCondition = LoggingUtil.getGuardedCondition(callExpression) ?: return
      if (guardIsUsed(guardedCondition)) return
      val loggerLevel = LoggingUtil.getLoggerLevel(callExpression) ?: return
      val levelFromCondition = LoggingUtil.getLevelFromCondition(guardedCondition) ?: return
      if (!LoggingUtil.isGuardedIn(levelFromCondition, loggerLevel)) {
        registerProblem(guardedCondition, levelFromCondition.name, loggerLevel.name)
      }
    }

    private fun guardIsUsed(guarded: UExpression): Boolean {
      val sourcePsi = guarded.sourcePsi ?: return false
      return CachedValuesManager.getManager(sourcePsi.project).getCachedValue(sourcePsi, CachedValueProvider {
        val guardedCondition = sourcePsi.toUElementOfType<UExpression>()
        if (guardedCondition == null) {
          return@CachedValueProvider CachedValueProvider.Result.create(false,
                                                                       PsiModificationTracker.MODIFICATION_COUNT)
        }
        val calls: List<UCallExpression> = LoggingUtil.getLoggerCalls(guardedCondition)
        val levelFromCondition = LoggingUtil.getLevelFromCondition(guardedCondition)
                                 ?: return@CachedValueProvider CachedValueProvider.Result.create(false,
                                                                                                 PsiModificationTracker.MODIFICATION_COUNT)
        return@CachedValueProvider CachedValueProvider.Result.create(calls.any { call ->
          val condition = LoggingUtil.getGuardedCondition(call)
          if ((guardedCondition.sourcePsi?.isEquivalentTo(condition?.sourcePsi)) != true) return@any false
          val loggerLevel = LoggingUtil.getLoggerLevel(call) ?: return@any false
          LoggingUtil.isGuardedIn(levelFromCondition, loggerLevel)
        }, PsiModificationTracker.MODIFICATION_COUNT)
      })
    }

    private fun processLegacyLoggers(callExpression: UCallExpression) {
      val guardedCondition = LoggingUtil.getGuardedCondition(callExpression) ?: return
      if (guardIsUsedLegacy(guardedCondition)) return
      val loggerLevel = LoggingUtil.getLegacyLoggerLevel(callExpression) ?: return
      val levelFromCondition = LoggingUtil.getLegacyLevelFromCondition(guardedCondition) ?: return
      if (!LoggingUtil.isLegacyGuardedIn(levelFromCondition, loggerLevel)) {
        registerProblem(guardedCondition, levelFromCondition.name, loggerLevel.name)
      }
    }

    private fun guardIsUsedLegacy(guarded: UExpression): Boolean {
      val sourcePsi = guarded.sourcePsi ?: return false
      return CachedValuesManager.getManager(sourcePsi.project).getCachedValue(sourcePsi, CachedValueProvider {
        val guardedCondition = sourcePsi.toUElementOfType<UExpression>()
        if (guardedCondition == null) {
          return@CachedValueProvider CachedValueProvider.Result.create(false,
                                                                       PsiModificationTracker.MODIFICATION_COUNT)
        }
        val calls: List<UCallExpression> = LoggingUtil.getLoggerCalls(guardedCondition)
        val levelFromCondition = LoggingUtil.getLegacyLevelFromCondition(guardedCondition)
                                 ?: return@CachedValueProvider CachedValueProvider.Result.create(false,
                                                                                                 PsiModificationTracker.MODIFICATION_COUNT)
        return@CachedValueProvider CachedValueProvider.Result.create(calls.any { call ->
          val condition = LoggingUtil.getGuardedCondition(call)
          if ((guardedCondition.sourcePsi?.isEquivalentTo(condition?.sourcePsi)) != true) return@any false
          val loggerLevel = LoggingUtil.getLegacyLoggerLevel(call) ?: return@any false
          LoggingUtil.isLegacyGuardedIn(levelFromCondition, loggerLevel)
        }, PsiModificationTracker.MODIFICATION_COUNT)
      })
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