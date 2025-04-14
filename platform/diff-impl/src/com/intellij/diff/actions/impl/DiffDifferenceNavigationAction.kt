// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.actions.impl

import com.intellij.diff.actions.impl.DiffDifferenceNavigationAction.Companion.isAvailable
import com.intellij.diff.tools.util.CrossFilePrevNextDifferenceIterableSupport
import com.intellij.diff.tools.util.DiffDataKeys
import com.intellij.diff.tools.util.PrevNextDifferenceIterable
import com.intellij.diff.tools.util.PrevNextFileIterable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.DumbAware

internal abstract class DiffDifferenceNavigationAction : AnAction(), DumbAware, ActionPromoter {
  init {
    isEnabledInModalContext = true
  }

  final override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  /**
   * @see [isAvailable]
   */
  final override fun update(e: AnActionEvent) {
    val iterable = e.getData(DiffDataKeys.PREV_NEXT_DIFFERENCE_ITERABLE)
    val fileIterable = e.getData(DiffDataKeys.PREV_NEXT_FILE_ITERABLE)
    val crossFileIterable = e.getData(DiffDataKeys.CROSS_FILE_PREV_NEXT_DIFFERENCE_ITERABLE)

    val available = isAvailable(iterable, fileIterable, crossFileIterable)

    e.presentation.isVisible = available
    e.presentation.isEnabled = false
    if (!available) {
      return
    }

    if (iterable != null && iterable.canNavigate()) {
      e.presentation.isEnabled = true
      return
    }

    if (fileIterable != null && crossFileIterable != null && fileIterable.canNavigate(true)) {
      e.presentation.isEnabled = true
      return
    }
  }

  protected abstract fun PrevNextDifferenceIterable.canNavigate(): Boolean
  protected abstract fun PrevNextFileIterable.canNavigate(fastCheck: Boolean): Boolean

  /**
   * Default shortcuts conflict with debugger actions,
   * and we would like to prevent accidental actions on iteration edges
   */
  override fun suppress(actions: List<AnAction>, context: DataContext): List<AnAction>? {
    if (isAvailable(context)) {
      return actions.filterNot { it == this }
    }
    return null
  }

  final override fun actionPerformed(e: AnActionEvent) {
    val iterable = e.getData(DiffDataKeys.PREV_NEXT_DIFFERENCE_ITERABLE)
    val crossFileIterable = e.getData(DiffDataKeys.CROSS_FILE_PREV_NEXT_DIFFERENCE_ITERABLE)

    if (iterable != null && iterable.canNavigate()) {
      iterable.navigate()
      crossFileIterable?.reset()
      return
    }

    val fileIterable = e.getData(DiffDataKeys.PREV_NEXT_FILE_ITERABLE)
    if (crossFileIterable == null || fileIterable == null || !fileIterable.canNavigate(false)) {
      return
    }

    if (crossFileIterable.canNavigateNow()) {
      fileIterable.navigate()
    }
    else {
      crossFileIterable.prepare(e.dataContext)
    }
  }

  protected abstract fun PrevNextDifferenceIterable.navigate()
  protected abstract fun PrevNextFileIterable.navigate()

  protected abstract fun CrossFilePrevNextDifferenceIterableSupport.canNavigateNow(): Boolean
  protected abstract fun CrossFilePrevNextDifferenceIterableSupport.prepare(dataContext: DataContext)

  companion object {
    private fun isAvailable(dataContext: DataContext): Boolean {
      val iterable = dataContext.getData(DiffDataKeys.PREV_NEXT_DIFFERENCE_ITERABLE)
      val fileIterable = dataContext.getData(DiffDataKeys.PREV_NEXT_FILE_ITERABLE)
      val crossFileIterable = dataContext.getData(DiffDataKeys.CROSS_FILE_PREV_NEXT_DIFFERENCE_ITERABLE)
      return isAvailable(iterable, fileIterable, crossFileIterable)
    }

    private fun isAvailable(
      iterable: PrevNextDifferenceIterable?,
      fileIterable: PrevNextFileIterable?,
      crossFileIterable: CrossFilePrevNextDifferenceIterableSupport?,
    ): Boolean = iterable != null || (fileIterable != null && crossFileIterable != null)
  }
}