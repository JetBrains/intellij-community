// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.actions.impl

import com.intellij.diff.tools.util.CrossFilePrevNextDifferenceIterableSupport
import com.intellij.diff.tools.util.PrevNextDifferenceIterable
import com.intellij.diff.tools.util.PrevNextFileIterable
import com.intellij.openapi.actionSystem.DataContext

internal open class DiffPreviousDifferenceAction : DiffDifferenceNavigationAction() {
  override fun PrevNextDifferenceIterable.canNavigate(): Boolean = canGoPrev()
  override fun PrevNextFileIterable.canNavigate(fastCheck: Boolean): Boolean = canGoPrev(fastCheck)
  override fun PrevNextDifferenceIterable.navigate() = goPrev()
  override fun PrevNextFileIterable.navigate() = goPrev(true)
  override fun CrossFilePrevNextDifferenceIterableSupport.canNavigateNow(): Boolean = canGoPrevNow()
  override fun CrossFilePrevNextDifferenceIterableSupport.prepare(dataContext: DataContext) = prepareGoPrev(dataContext)

  companion object {
    const val ID = "PreviousDiff"
  }
}
