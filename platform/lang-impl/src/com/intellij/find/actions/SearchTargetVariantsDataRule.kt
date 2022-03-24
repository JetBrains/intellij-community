// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.find.actions

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.find.findUsages.PsiElement2UsageTargetAdapter
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.usages.UsageTarget
import com.intellij.usages.UsageView
import com.intellij.util.SmartList

/**
 * @see com.intellij.codeInsight.navigation.actions.GotoDeclarationAction.doChooseAmbiguousTarget
 */
internal fun targetVariants(dc: DataContext): List<TargetVariant> {
  val allTargets = SmartList<TargetVariant>()

  dc.getData(FindUsagesAction.SEARCH_TARGETS)?.mapTo(allTargets, ::SearchTargetVariant)

  val usageTargets: Array<out UsageTarget>? = dc.getData(UsageView.USAGE_TARGETS_KEY)
  if (usageTargets == null) {
    val editor = dc.getData(CommonDataKeys.EDITOR)
    if (editor != null) {
      val offset = editor.caretModel.offset
      try {
        val reference = TargetElementUtil.findReference(editor, offset)
        if (reference != null) {
          TargetElementUtil.getInstance().getTargetCandidates(reference).mapTo(allTargets, ::PsiTargetVariant)
        }
      }
      catch (ignore: IndexNotReadyException) {
      }
    }
  }
  else if (usageTargets.isNotEmpty()) {
    val target: UsageTarget = usageTargets[0]
    if (target is PsiElement2UsageTargetAdapter) {
      target.element?.let {
        allTargets += PsiTargetVariant(it)
      }
    }
    else {
      allTargets += CustomTargetVariant(target)
    }
  }

  return allTargets
}
