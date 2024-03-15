// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.logging

import com.intellij.analysis.JvmAnalysisBundle
import com.intellij.codeInspection.*
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandQuickFix
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.uast.UastHintedVisitorAdapter
import org.jetbrains.uast.*
import org.jetbrains.uast.generate.getUastElementFactory
import org.jetbrains.uast.generate.replace
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
        registerProblem(guardedCondition, levelFromCondition.name, loggerLevel.name, callExpression, guardedCondition)
      }
    }

    private fun guardIsUsed(guarded: UExpression): Boolean {
      val sourcePsi = guarded.sourcePsi ?: return false
      val guardedCondition = sourcePsi.toUElementOfType<UExpression>()
      if (guardedCondition == null) {
        return false
      }
      val calls: List<UCallExpression> = LoggingUtil.getLoggerCalls(guardedCondition)
      val levelFromCondition = LoggingUtil.getLevelFromCondition(guardedCondition) ?: return false
      return calls.any { call ->
        val condition = LoggingUtil.getGuardedCondition(call)
        if ((guardedCondition.sourcePsi?.isEquivalentTo(condition?.sourcePsi)) != true) return@any false
        val loggerLevel = LoggingUtil.getLoggerLevel(call) ?: return@any false
        LoggingUtil.isGuardedIn(levelFromCondition, loggerLevel)
      }
    }

    private fun processLegacyLoggers(callExpression: UCallExpression) {
      val guardedCondition = LoggingUtil.getGuardedCondition(callExpression) ?: return
      if (guardIsUsedLegacy(guardedCondition)) return
      val loggerLevel = LoggingUtil.getLegacyLoggerLevel(callExpression) ?: return
      val levelFromCondition = LoggingUtil.getLegacyLevelFromCondition(guardedCondition) ?: return
      if (!LoggingUtil.isLegacyGuardedIn(levelFromCondition, loggerLevel)) {
        registerProblem(guardedCondition, levelFromCondition.name, loggerLevel.name, callExpression, guardedCondition)
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
                                loggerLevel: String,
                                callExpression: UCallExpression? = null,
                                guardedCondition: UExpression? = null) {
      val fixes = createFixes(callExpression, guardedCondition)
      val message = JvmAnalysisBundle.message("jvm.inspection.logging.condition.disagrees.with.log.statement.problem.descriptor",
                                              levelFromCondition, loggerLevel)
      if (call is UCallExpression) {
        holder.registerUProblem(call, message, *fixes)
      }
      if (call is UReferenceExpression) {
        holder.registerUProblem(call, message, *fixes)
      }
    }

    private fun createFixes(callExpression: UCallExpression?, guardedCondition: UExpression?): Array<LocalQuickFix> {
      if (callExpression == null || guardedCondition == null) {
        return emptyArray()
      }
      val result = mutableListOf<LocalQuickFix>()
      val logFix = createChangeLog(callExpression, guardedCondition)
      if (logFix != null) {
        result.add(logFix)
      }
      val guardFix = createChangeGuard(guardedCondition, callExpression)
      if (guardFix != null) {
        result.add(guardFix)
      }
      return result.toTypedArray()
    }

    private fun createChangeGuard(guard: UExpression, callExpression: UCallExpression): LocalQuickFix? {
      val guardName = getGuardName(guard) ?: return null
      LoggingUtil.GUARD_MAP[guardName] ?: return null
      if (LoggingUtil.getLoggerCalls(guard).size != 1) return null
      val callToGuard = LoggingUtil.GUARD_MAP.entries.firstOrNull { it.value == callExpression.methodName }?.key ?: return null
      return ChangeCallNameFix(FixType.GUARD, guard, callToGuard)
    }

    private fun createChangeLog(callExpression: UCallExpression, guard: UExpression): LocalQuickFix? {
      val guardName = getGuardName(guard) ?: return null
      val guardToCall = LoggingUtil.GUARD_MAP[guardName] ?: return null
      if (!LoggingUtil.GUARD_MAP.values.contains(callExpression.methodName)) return null
      return ChangeCallNameFix(FixType.CALL, callExpression, guardToCall)
    }

    private fun getGuardName(guard: UExpression): String? {
      return when (guard) {
        is UCallExpression -> {
          guard.methodName
        }
        is UQualifiedReferenceExpression -> {
          guard.resolvedName
        }
        else -> {
          null
        }
      }
    }
  }
}

private fun ChangeCallNameFix(fixType: FixType, expression: UExpression, newName: String): ChangeCallNameFix? {
  val sourcePsi = expression.sourcePsi ?: return null
  val pointer = SmartPointerManager.createPointer(sourcePsi)
  return ChangeCallNameFix(fixType, pointer, newName)
}

private enum class FixType { CALL, GUARD }
private class ChangeCallNameFix(private val myFixType: FixType,
                                private val myExpression: SmartPsiElementPointer<PsiElement?>,
                                private val newName: String) : PsiUpdateModCommandQuickFix() {

  override fun getName(): String {
    return JvmAnalysisBundle.message("jvm.inspection.logging.condition.disagrees.with.log.statement.fix.name",
                                     if (myFixType == FixType.GUARD) 0 else 1)
  }

  override fun getFamilyName(): String {
    return JvmAnalysisBundle.message("jvm.inspection.logging.condition.disagrees.with.log.statement.fix.family.name")
  }

  override fun applyFix(project: Project, element: PsiElement, updater: ModPsiUpdater) {
    val uCallExpression = if (myFixType == FixType.GUARD) {
      val referenceExpression = myExpression.element.toUElementOfType<UCallExpression>()
                                ?: myExpression.element.toUElementOfType<UQualifiedReferenceExpression>() ?: return
      if (referenceExpression is UCallExpression) {
        element.getUastParentOfType<UCallExpression>() ?: return
      }
      else {
        element.getUastParentOfType<UQualifiedReferenceExpression>() ?: return
      }
    }
    else {
      val uCallExpression = myExpression.element.toUElementOfType<UCallExpression>() ?: return
      val psiElement = PsiTreeUtil.findSameElementInCopy(uCallExpression.sourcePsi, element.containingFile) ?: return
      psiElement.toUElementOfType<UCallExpression>() ?: return
    }
    val elementFactory = uCallExpression.getUastElementFactory(project) ?: return

    if (uCallExpression is UCallExpression) {
      val newCall = elementFactory.createCallExpression(uCallExpression.receiver, newName, uCallExpression.valueArguments,
                                                        uCallExpression.returnType,
                                                        uCallExpression.kind, uCallExpression.sourcePsi
      ) ?: return
      val oldCall = uCallExpression.getQualifiedParentOrThis()
      oldCall.replace(newCall)
    }
    else if (uCallExpression is UQualifiedReferenceExpression) {
      val receiver = uCallExpression.receiver.sourcePsi?.text ?: return
      val newReference: UQualifiedReferenceExpression =
        elementFactory.createQualifiedReference(qualifiedName = "$receiver.$newName",
                                                context = uCallExpression.sourcePsi) ?: return
      uCallExpression.replace(newReference)
    }
    return
  }
}