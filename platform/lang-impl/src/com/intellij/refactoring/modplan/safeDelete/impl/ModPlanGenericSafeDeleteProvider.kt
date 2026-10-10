// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.modplan.safeDelete.impl

import com.intellij.model.SymbolPointer
import com.intellij.psi.PsiNamedElement
import com.intellij.refactoring.modplan.ModPlanAnalysisResult
import com.intellij.refactoring.modplan.ModPlanChain
import com.intellij.refactoring.modplan.ModPlanFinalStep
import com.intellij.refactoring.modplan.ModPlanChainBuilder
import com.intellij.refactoring.modplan.safeDelete.ModPlanSafeDeleteProvider
import com.intellij.refactoring.modplan.safeDelete.ModPlanSafeDeleteProvider.DeletionDecision
import com.intellij.refactoring.modplan.safeDelete.ModPlanSafeDeleteUsageInfoUtil
import com.intellij.refactoring.safeDelete.NonCodeUsageSearchInfo
import com.intellij.refactoring.safeDelete.SafeDeleteProcessor
import com.intellij.usageView.UsageInfo
import com.intellij.util.containers.MultiMap
import java.util.Collections

/**
 * The provider of named elements and files that no language provider owns. It is not registered: the engine falls back to it.
 * It cannot edit anything, so every reference to the element is a conflict.
 */
internal object ModPlanGenericSafeDeleteProvider : ModPlanSafeDeleteProvider {
  override fun isApplicable(subject: SymbolPointer): Boolean {
    return subject.element() is PsiNamedElement
  }

  override fun prepareRequest(
    chainBuilder: ModPlanChainBuilder,
    subject: SymbolPointer,
    deletedSubjects: List<SymbolPointer>,
  ): ModPlanChain<DeletionDecision> {
    if (subject.element() == null) return chainBuilder.passValue(DeletionDecision.REFUSED)
    if (subject !in chainBuilder.request.subjects()) return chainBuilder.passValue(DeletionDecision.DELETED)
    return chainBuilder.ask(ModPlanSafeDeleteUsageInfoUtil.searchForm(ModPlanSettingsSearchStorage)) { it.passValue(DeletionDecision.DELETED) }
  }

  override fun analyze(
    chainBuilder: ModPlanChainBuilder,
    subject: SymbolPointer,
    deletedSubjects: List<SymbolPointer>,
  ): ModPlanChain<ModPlanFinalStep<ModPlanAnalysisResult>> {
    // The analysis asks nothing, so the search runs in the step at the end of the run, after every question.
    return chainBuilder.finish { request ->
      val target = subject.element()
                   ?: return@finish ModPlanAnalysisResult.Refused(ModPlanAnalysisResult.RefusedReason.NOT_APPLICABLE)
      val elementsToDelete = ModPlanSafeDeleteUsageInfoUtil.elementsOf(deletedSubjects)
      val foundUsages = Collections.synchronizedList(ArrayList<UsageInfo>())
      SafeDeleteProcessor.findGenericElementUsages(target, foundUsages, elementsToDelete)
      val info = NonCodeUsageSearchInfo(SafeDeleteProcessor.getDefaultInsideDeletedCondition(elementsToDelete), target)
      // Every code usage of the generic search blocks the deletion, so each becomes a broken conflict.
      val usages = ModPlanSafeDeleteUsageInfoUtil.withNonCodeUsages(info, foundUsages, request.project(), request.optionValues())
      ModPlanSafeDeleteUsageInfoUtil.analyzeUsages(target, usages, usages, MultiMap(), null)
    }
  }
}
