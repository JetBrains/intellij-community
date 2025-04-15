// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.actions.impl

import com.intellij.diff.tools.util.PrevNextFileIterable

internal open class DiffNextFileAction : DiffFileNavigationAction() {
  override fun PrevNextFileIterable.canNavigate(fastCheck: Boolean): Boolean = canGoNext(fastCheck)
  override fun PrevNextFileIterable.navigate() = goNext(false)

  companion object {
    const val ID: String = "Diff.NextChange"
  }
}
