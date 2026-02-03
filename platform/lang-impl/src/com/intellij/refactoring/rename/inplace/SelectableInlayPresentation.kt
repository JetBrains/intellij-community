// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.rename.inplace

import com.intellij.codeInsight.hints.presentation.InlayPresentation

interface SelectableInlayPresentation: InlayPresentation {
  var isSelected: Boolean
  fun addSelectionListener(listener: SelectionListener)

  fun interface SelectionListener {
    fun selectionChanged(isSelected: Boolean)
  }
}
