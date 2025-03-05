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
    if (context.getData(DiffDataKeys.PREV_NEXT_FILE_ITERABLE) != null && actions.any(::isFileNavigationAction)) {
      return actions.filterNot(::isFileNavigationAction)
    }

    if (context.getData(DiffDataKeys.PREV_NEXT_DIFFERENCE_ITERABLE) != null && actions.any(::isDifferenceNavigationAction)) {
      return actions.filterNot(::isDifferenceNavigationAction)
    }

    if (context.getData(DiffDataKeys.NAVIGATABLE_ARRAY) != null && actions.any(::isSourceNavigationAction)) {
      return actions.filterNot(::isSourceNavigationAction)
    }
    return emptyList()
  }
}

private fun isFileNavigationAction(action: AnAction) = action is DiffNextFileAction || action is DiffPreviousFileAction

private fun isDifferenceNavigationAction(action: AnAction) = action is DiffNextDifferenceAction || action is DiffPreviousDifferenceAction

private fun isSourceNavigationAction(action: AnAction) = action is OpenInEditorAction