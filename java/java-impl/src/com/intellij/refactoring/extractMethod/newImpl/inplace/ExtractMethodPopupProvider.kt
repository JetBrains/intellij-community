// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod.newImpl.inplace

import com.intellij.java.refactoring.JavaRefactoringBundle
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.layout.*

class ExtractMethodPopupProvider(showStatic: Boolean, isStatic: Boolean, isAnnotated: Boolean) {
  data class PanelState(val annotate: Boolean, val makeStatic: Boolean)

  private val annotateCheckBox = JBCheckBox(JavaRefactoringBundle.message("extract.method.checkbox.annotate")).apply { isSelected = isAnnotated }
  private val makeStaticCheckBox = JBCheckBox(JavaRefactoringBundle.message("extract.method.checkbox.make.static")).apply { isSelected = isStatic }

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
      link(JavaRefactoringBundle.message("extract.method.link.label.go.to.method"), null) {}
      comment(KeymapUtil.getFirstKeyboardShortcutText("GotoDeclaration"))
    }
    row {
      link(JavaRefactoringBundle.message("extract.method.link.label.show.dialog"), null) {}
      comment(KeymapUtil.getFirstKeyboardShortcutText("ExtractMethod"))
    }
  }
}