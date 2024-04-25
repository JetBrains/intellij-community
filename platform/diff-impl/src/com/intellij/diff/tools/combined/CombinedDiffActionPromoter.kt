// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.combined

import com.intellij.openapi.actionSystem.ActionPromoter
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ex.ActionUtil

class CombinedDiffActionPromoter : ActionPromoter {
  override fun promote(actions: List<AnAction>, context: DataContext): List<AnAction> {
    if (context.getData(COMBINED_DIFF_VIEWER) == null) return actions

    val caret = CommonDataKeys.CARET.getData(context)
    if (caret != null) {
      return actions.filterNot {
        ActionUtil.getDelegateChainRootAction(it) is CombinedGlobalBlockNavigationAction
      }
    }

    return actions
  }
}
