// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.util

import com.intellij.util.concurrency.annotations.RequiresEdt

internal interface PrevNextFileIterable {
  /**
   * It must be used before proceeding to the previous change.
   *
   * @param fastCheckOnly if `true`, the check is performed as part of a fast update process (typically from an update method);
   * if `false`, a more comprehensive check is performed with a full update.
   */
  @RequiresEdt
  fun canGoPrev(fastCheckOnly: Boolean): Boolean

  /**
   * It must be used before proceeding to the next change.
   *
   * @param fastCheckOnly if `true`, the check is performed as part of a fast update process (typically from an update method);
   * if `false`, a more comprehensive check is performed with a full update.
   */
  @RequiresEdt
  fun canGoNext(fastCheckOnly: Boolean): Boolean

  /**
   * Use to proceed to the previous change.
   *
   * @param showLastChange Used `true` when guaranteeing synchronous calls during external state updates.
   */
  @RequiresEdt
  fun goPrev(showLastChange: Boolean)

  /**
   * Use to proceed to the next change.
   *
   * @param showFirstChange Used `true` when guaranteeing synchronous calls during external state updates.
   */
  @RequiresEdt
  fun goNext(showFirstChange: Boolean)
}
