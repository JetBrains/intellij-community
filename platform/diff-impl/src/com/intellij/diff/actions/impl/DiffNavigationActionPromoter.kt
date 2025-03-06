// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.actions.impl

import com.intellij.diff.tools.util.DiffDataKeys
import com.intellij.openapi.actionSystem.ActionPromoter
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataContext

/**
 * When iterating over diff, iteration actions take precedence over anything else
 */
internal class DiffNavigationActionPromoter : ActionPromoter {
  override fun suppress(actions: List<AnAction>, context: DataContext): List<AnAction> {
    if (actions.any(::isFileNavigationAction) && DiffFileNavigationAction.isAvailable(context)) {
      return actions.filterNot(::isFileNavigationAction)
    }

    if (actions.any(::isDifferenceNavigationAction) && DiffDifferenceNavigationAction.isAvailable(context)) {
      return actions.filterNot(::isDifferenceNavigationAction)
    }

    if (context.getData(DiffDataKeys.NAVIGATABLE_ARRAY) != null && actions.any(::isSourceNavigationAction)) {
      return actions.filterNot(::isSourceNavigationAction)
    }
    return emptyList()
  }
}

private fun isFileNavigationAction(action: AnAction) = action is DiffFileNavigationAction

private fun isDifferenceNavigationAction(action: AnAction) = action is DiffDifferenceNavigationAction

private fun isSourceNavigationAction(action: AnAction) = action is OpenInEditorAction