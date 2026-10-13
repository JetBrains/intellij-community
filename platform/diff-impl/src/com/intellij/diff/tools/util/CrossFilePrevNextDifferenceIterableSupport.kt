// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.util

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.CalledInAny

internal interface CrossFilePrevNextDifferenceIterableSupport {
  /**
   * Check if the next file should be displayed to view the next difference
   *
   * @return true if the next file should be displayed, false if the [prepareGoNext] should be called first
   */
  @RequiresEdt
  fun canGoNextNow(): Boolean

  /**
   * Check if the prev file should be displayed to view the prev difference
   *
   * @return true if the next file should be displayed, false if the [prepareGoPrev] should be called first
   */
  @RequiresEdt
  fun canGoPrevNow(): Boolean

  /**
   * Prepare to move to the next file
   */
  @RequiresEdt
  fun prepareGoNext(dataContext: DataContext)

  /**
   * Prepare to move to the prev file
   */
  @RequiresEdt
  fun prepareGoPrev(dataContext: DataContext)

  /**
   * Reset any navigation preparations
   */
  @CalledInAny
  fun reset()
}