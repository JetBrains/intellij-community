// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.find.actions

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.find.findUsages.PsiElement2UsageTargetAdapter
import com.intellij.ide.impl.dataRules.GetDataRule
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.usages.UsageTarget
import com.intellij.usages.UsageView
import com.intellij.util.SmartList

/**
 * @see com.intellij.codeInsight.navigation.actions.GotoDeclarationAction.doChooseAmbiguousTarget
 */
class SearchTargetVariantsDataRule : GetDataRule {

  override fun getData(dataProvider: DataProvider): Any? {
    val allTargets = SmartList<TargetVariant>()

    FindUsagesAction.SEARCH_TARGETS.getData(dataProvider)?.mapTo(allTargets, ::SearchTargetVariant)

    val usageTargets: Array<out UsageTarget>? = UsageView.USAGE_TARGETS_KEY.getData(dataProvider)
    if (usageTargets == null) {
      val editor = CommonDataKeys.EDITOR.getData(dataProvider)
      if (editor != null) {
        val offset = editor.caretModel.offset
        try {
          val reference = TargetElementUtil.findReference(editor, offset)
          if (reference != null) {
            TargetElementUtil.getInstance().getTargetCandidates(reference).mapTo(allTargets, ::PsiTargetVariant)
          }
        }
        catch (ignore: IndexNotReadyException) { }
      }
    }
    else if (usageTargets.isNotEmpty()) {
      val target: UsageTarget = usageTargets[0]
      allTargets += if (target is PsiElement2UsageTargetAdapter) {
        PsiTargetVariant(target.element)
      }
      else {
        CustomTargetVariant(target)
      }
    }

    return allTargets.takeUnless {
      it.isEmpty()
    }
  }
}
