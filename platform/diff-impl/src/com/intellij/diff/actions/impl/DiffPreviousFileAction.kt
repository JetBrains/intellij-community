// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.actions.impl

import com.intellij.diff.tools.util.PrevNextFileIterable

internal class DiffPreviousFileAction : DiffFileNavigationAction() {
  override fun PrevNextFileIterable.canNavigate(fastCheck: Boolean): Boolean = canGoPrev(fastCheck)
  override fun PrevNextFileIterable.navigate() = goPrev(false)

  companion object {
    const val ID: String = "Diff.PrevChange"
  }
}
