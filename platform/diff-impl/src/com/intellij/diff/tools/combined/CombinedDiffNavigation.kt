// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.combined

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface CombinedDiffNavigation {
  // chunks navigation
  fun canGoNextDiff(): Boolean
  fun canGoPrevDiff(): Boolean

  fun goNextDiff()
  fun goPrevDiff()

  // blocks navigation
  fun canGoNextBlock(): Boolean
  fun canGoPrevBlock(): Boolean

  fun goNextBlock()
  fun goPrevBlock()
}
