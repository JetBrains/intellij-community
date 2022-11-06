// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod.newImpl.inplace

import com.intellij.java.refactoring.JavaRefactoringBundle
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.LayoutFocusTraversalPolicy

class ExtractMethodPopupProvider(val annotateDefault: Boolean? = null,
                                 val makeStaticDefault: Boolean? = null,
                                 val staticPassFields: Boolean = false) {

  var annotate = annotateDefault
    private set

  var makeStatic = makeStaticDefault
    private set

  val isChanged: Boolean
    get() = annotate != annotateDefault || makeStatic != makeStaticDefault

  private var changeListener: () -> Unit = {}

  fun setChangeListener(listener: () -> Unit) {
    changeListener = listener
  }

  private var showDialogAction: (AnActionEvent?) -> Unit = {}
  fun setShowDialogAction(action: (AnActionEvent?) -> Unit) {
    showDialogAction = action
  }

  private val ACTION_EXTRACT_METHOD = "ExtractMethod"

  val panel: JPanel by lazy { createPanel() }

  private val makeStaticLabel = if (staticPassFields) {
    JavaRefactoringBundle.message("extract.method.checkbox.make.static.and.pass.fields")
  } else {
    JavaRefactoringBundle.message("extract.method.checkbox.make.static")
  }

  private fun createPanel(): DialogPanel {
    var hasFocusedElement = false
    fun Cell<JComponent>.setFocusIfEmpty() {
      if (! hasFocusedElement) {
        hasFocusedElement = true
        focused()
      }
    }
    val panel = panel {
      if (annotate != null) {
        row {
          checkBox(JavaRefactoringBundle.message("extract.method.checkbox.annotate"))
            .applyToComponent {
              isSelected = annotate ?: false
              addActionListener {
                annotate = isSelected
                changeListener.invoke()
              }
            }.setFocusIfEmpty()
        }
      }
      if (makeStatic != null) {
        row {
          checkBox(makeStaticLabel)
            .applyToComponent {
              isSelected = makeStatic ?: false
              addActionListener {
                makeStatic = isSelected
                changeListener.invoke()
              }
            }.setFocusIfEmpty()
        }
      }
      row {
        link(JavaRefactoringBundle.message("extract.method.link.label.more.options")) { showDialogAction(null) }
          .gap(RightGap.SMALL)
        comment(KeymapUtil.getFirstKeyboardShortcutText(ACTION_EXTRACT_METHOD))
      }
    }
    panel.isFocusCycleRoot = true
    panel.focusTraversalPolicy = LayoutFocusTraversalPolicy()

    DumbAwareAction.create {
      showDialogAction(it)
    }.registerCustomShortcutSet(KeymapUtil.getActiveKeymapShortcuts(ACTION_EXTRACT_METHOD), panel)

    return panel
  }

}