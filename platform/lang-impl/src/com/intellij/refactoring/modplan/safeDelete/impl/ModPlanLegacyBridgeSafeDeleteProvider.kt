// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.modplan.safeDelete.impl

import com.intellij.model.SymbolPointer
import com.intellij.refactoring.modplan.ModPlanAnalysisResult
import com.intellij.refactoring.modplan.ModPlanChain
import com.intellij.refactoring.modplan.ModPlanFinalStep
import com.intellij.refactoring.modplan.ModPlanChainBuilder
import com.intellij.refactoring.modplan.safeDelete.ModPlanLegacySafeDeleteProvider
import com.intellij.refactoring.modplan.safeDelete.ModPlanSafeDeleteProvider
import com.intellij.refactoring.safeDelete.SafeDeleteProcessorDelegate
import org.jetbrains.annotations.ApiStatus

/**
 * The one registered provider of the legacy [SafeDeleteProcessorDelegate]s, so that the engine knows only [ModPlanSafeDeleteProvider].
 *
 * The owner of an element is the first legacy delegate in the legacy order that handles it, as in the interactive safe delete. The
 * element is supported if that delegate returns an adapter from [SafeDeleteProcessorDelegate.asModPlan], and the adapter supports it;
 * every other call goes to the adapter. It is registered last, so that a provider of the new extension point wins over the legacy delegate
 * of the same element.
 */
@ApiStatus.Internal
class ModPlanLegacyBridgeSafeDeleteProvider : ModPlanSafeDeleteProvider {
  override fun isApplicable(subject: SymbolPointer): Boolean {
    val element = subject.element() ?: return false
    return ModPlanLegacySafeDeleteProvider.legacyHandlerOf(element) != null
  }

  override fun isLegacyOnly(subject: SymbolPointer): Boolean = adapterOf(subject)?.isLegacyOnly(subject) ?: true

  override fun prepareRequest(
    chainBuilder: ModPlanChainBuilder,
    subject: SymbolPointer,
    deletedSubjects: List<SymbolPointer>,
  ): ModPlanChain<ModPlanSafeDeleteProvider.DeletionDecision> {
    return adapterOf(subject)?.prepareRequest(chainBuilder, subject, deletedSubjects)
           ?: chainBuilder.passValue(ModPlanSafeDeleteProvider.DeletionDecision.REFUSED)
  }

  override fun analyze(
    chainBuilder: ModPlanChainBuilder,
    subject: SymbolPointer,
    deletedSubjects: List<SymbolPointer>,
  ): ModPlanChain<ModPlanFinalStep<ModPlanAnalysisResult>> {
    return adapterOf(subject)?.analyze(chainBuilder, subject, deletedSubjects)
           ?: chainBuilder.finish { ModPlanAnalysisResult.Refused(ModPlanAnalysisResult.RefusedReason.NOT_SUPPORTED) }
  }

  private fun adapterOf(subject: SymbolPointer): ModPlanSafeDeleteProvider? {
    val element = subject.element() ?: return null
    return ModPlanLegacySafeDeleteProvider.legacyHandlerOf(element)?.asModPlan()
  }
}
