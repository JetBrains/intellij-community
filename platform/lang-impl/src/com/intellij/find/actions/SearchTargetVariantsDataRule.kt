// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.find.actions

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.find.FindManager
import com.intellij.find.findUsages.PsiElement2UsageTargetAdapter
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.usages.UsageTarget
import com.intellij.usages.UsageView
import com.intellij.util.SmartList

/**
 * @see com.intellij.codeInsight.navigation.actions.GotoDeclarationAction.chooseAmbiguousTarget
 */
internal fun targetVariants(dc: DataContext): List<TargetVariant> {
  // in the dumb mode, only a dumb-aware target finds its usages: the find usages handler of a PSI element needs the indexes
  val dumbService = dc.getData(CommonDataKeys.PROJECT)?.let(DumbService::getInstance)
  val dumb = dumbService != null && dumbService.isDumb
  return targetVariants(dc, withPsi = !dumb) { dumbService == null || dumbService.isUsableInCurrentContext(it) }
}

/** The targets that find their usages in the smart mode, also in the dumb mode. */
internal fun targetVariantsInSmartMode(dc: DataContext): List<TargetVariant> {
  return targetVariants(dc, withPsi = true) { true }
}

private fun targetVariants(dc: DataContext, withPsi: Boolean, isUsable: (Any) -> Boolean): List<TargetVariant> {
  val allTargets = SmartList<TargetVariant>()

  dc.getData(FindUsagesAction.SEARCH_TARGETS)?.filter(isUsable)?.mapTo(allTargets, ::SearchTargetVariant)

  val usageTargets: Array<out UsageTarget>? = dc.getData(UsageView.USAGE_TARGETS_KEY)
  if (usageTargets == null) {
    val editor = dc.getData(CommonDataKeys.EDITOR)
    if (editor != null && withPsi) {
      val offset = editor.caretModel.offset
      try {
        val reference = TargetElementUtil.findReference(editor, offset)
        if (reference != null) {
          // a candidate without a find usages handler does nothing when chosen
          val findManager = FindManager.getInstance(reference.element.project)
          TargetElementUtil.getInstance().getTargetCandidates(reference)
            .filter(findManager::canFindUsages)
            .mapTo(allTargets, ::PsiTargetVariant)
        }
      }
      catch (ignore: IndexNotReadyException) {
      }
    }
  }
  else if (usageTargets.isNotEmpty()) {
    val target: UsageTarget = usageTargets[0]
    if (target is PsiElement2UsageTargetAdapter) {
      if (withPsi) {
        target.element?.let {
          allTargets += PsiTargetVariant(it)
        }
      }
    }
    else if (isUsable(target)) {
      allTargets += CustomTargetVariant(target)
    }
  }

  return allTargets
}
