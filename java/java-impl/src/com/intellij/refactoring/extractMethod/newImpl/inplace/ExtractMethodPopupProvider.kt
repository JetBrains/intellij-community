// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod.newImpl.inplace

import com.intellij.java.refactoring.JavaRefactoringBundle
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.layout.*
import javax.swing.JComponent
import javax.swing.LayoutFocusTraversalPolicy

class ExtractMethodPopupProvider(annotateNullability: Boolean? = null,
                                 makeStatic: Boolean? = null,
                                 staticPassFields: Boolean = false) {

  var annotate = annotateNullability
    private set

  var makeStatic = makeStatic
    private set

  private var changeListener: () -> Unit = {}

  fun setChangeListener(listener: () -> Unit) {
    changeListener = listener
  }

  private var showDialogAction: () -> Unit = {}
  fun setShowDialogAction(action: () -> Unit) {
    showDialogAction = action
  }

  private var navigateMethodAction: () -> Unit = {}
  fun setNavigateMethodAction(action: () -> Unit) {
    navigateMethodAction = action
  }

  private val ACTION_EXTRACT_METHOD = "ExtractMethod"

  val panel: DialogPanel by lazy { createPanel() }

  val makeStaticLabel = if (staticPassFields) {
    JavaRefactoringBundle.message("extract.method.checkbox.make.static.and.pass.fields")
  } else {
    JavaRefactoringBundle.message("extract.method.checkbox.make.static")
  }

  private fun createPanel(): DialogPanel {
    var hasFocusedElement = false
    fun CellBuilder<JComponent>.setFocusIfEmpty() {
      if (! hasFocusedElement) {
        hasFocusedElement = true
        focused()
      }
    }
    val panel = panel {
      if (annotate != null) {
        row {
          checkBox(JavaRefactoringBundle.message("extract.method.checkbox.annotate"), annotate ?: false) { _, checkBox -> annotate = checkBox.isSelected; changeListener.invoke() }
            .setFocusIfEmpty()
        }
      }
      if (makeStatic != null) {
        row {
          checkBox(makeStaticLabel, makeStatic ?: false) { _, checkBox -> makeStatic = checkBox.isSelected; changeListener.invoke() }
            .setFocusIfEmpty()
        }
      }
      row {
        cell {
          link(JavaRefactoringBundle.message("extract.method.link.label.go.to.declaration"), null) { navigateMethodAction() }
            .applyToComponent { isFocusable = true }
            .setFocusIfEmpty()
          comment(KeymapUtil.getFirstKeyboardShortcutText(IdeActions.ACTION_GOTO_DECLARATION))
        }
      }
      row {
        cell {
          link(JavaRefactoringBundle.message("extract.method.link.label.more.options"), null) { showDialogAction() }
            .applyToComponent { isFocusable = true }
          comment(KeymapUtil.getFirstKeyboardShortcutText(ACTION_EXTRACT_METHOD))
        }
      }
    }
    panel.isFocusCycleRoot = true
    panel.focusTraversalPolicy = LayoutFocusTraversalPolicy()
    DumbAwareAction.create {
      navigateMethodAction()
    }.registerCustomShortcutSet(KeymapUtil.getActiveKeymapShortcuts(IdeActions.ACTION_GOTO_DECLARATION), panel)

    DumbAwareAction.create {
      showDialogAction()
    }.registerCustomShortcutSet(KeymapUtil.getActiveKeymapShortcuts(ACTION_EXTRACT_METHOD), panel)

    return panel
  }

}