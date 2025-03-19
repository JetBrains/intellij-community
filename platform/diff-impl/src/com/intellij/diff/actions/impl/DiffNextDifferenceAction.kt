// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.actions.impl

import com.intellij.diff.tools.util.CrossFilePrevNextDifferenceIterableSupport
import com.intellij.diff.tools.util.PrevNextDifferenceIterable
import com.intellij.diff.tools.util.PrevNextFileIterable
import com.intellij.openapi.actionSystem.DataContext

internal open class DiffNextDifferenceAction : DiffDifferenceNavigationAction() {
  override fun PrevNextDifferenceIterable.canNavigate(): Boolean = canGoNext()
  override fun PrevNextFileIterable.canNavigate(fastCheck: Boolean): Boolean = canGoNext(fastCheck)
  override fun PrevNextDifferenceIterable.navigate() = goNext()
  override fun PrevNextFileIterable.navigate() = goNext(true)
  override fun CrossFilePrevNextDifferenceIterableSupport.canNavigateNow(): Boolean = canGoNextNow()
  override fun CrossFilePrevNextDifferenceIterableSupport.prepare(dataContext: DataContext) = prepareGoNext(dataContext)

  companion object {
    const val ID = "NextDiff"
  }
}
