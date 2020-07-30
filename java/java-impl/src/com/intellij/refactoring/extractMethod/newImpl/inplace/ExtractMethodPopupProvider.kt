// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod.newImpl.inplace

import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.layout.*

class ExtractMethodPopupProvider(showStatic: Boolean, isStatic: Boolean, isAnnotated: Boolean) {
  data class PanelState(val annotate: Boolean, val makeStatic: Boolean)

  private val annotateCheckBox = JBCheckBox("Annotate").apply { isSelected = isAnnotated }
  private val makeStaticCheckBox = JBCheckBox("Make static").apply { isSelected = isStatic }

  val state
    get() = PanelState(annotateCheckBox.isSelected, makeStaticCheckBox.isSelected)

  fun setStateListener(listener: (PanelState) -> Unit) {
    sequenceOf(annotateCheckBox, makeStaticCheckBox).forEach { checkBox ->
      checkBox.actionListeners.forEach(checkBox::removeActionListener)
      checkBox.addActionListener { listener(state) }
    }
  }

  val panel = panel {
    row { annotateCheckBox() }
    if (showStatic) row { makeStaticCheckBox() }
    row {
      link("Go to method", null) {}
      comment("Ctrl+B")
    }
    row {
      link("Show dialog", null) {}
      comment("Ctrl+Alt+M")
    }
  }
}