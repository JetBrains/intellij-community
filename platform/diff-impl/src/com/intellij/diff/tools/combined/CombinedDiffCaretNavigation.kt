// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.combined

interface CombinedDiffCaretNavigation {
  fun moveCaretToNextBlock()

  fun moveCaretToPrevBlock()

  fun moveCaretPageUp()

  fun moveCaretPageDown()
}