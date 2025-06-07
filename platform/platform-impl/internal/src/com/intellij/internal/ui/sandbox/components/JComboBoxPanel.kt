// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui.sandbox.components

import com.intellij.internal.ui.sandbox.UISandboxPanel
import com.intellij.internal.ui.sandbox.items
import com.intellij.internal.ui.sandbox.withStateLabel
import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComboBox
import javax.swing.JComponent

internal class JComboBoxPanel : UISandboxPanel {

  override val title: String = "JComboBox"

  override fun createContent(disposable: Disposable): JComponent {
    val items = items(10)

    val result = panel {
      withStateLabel {
        jComboBox(items)
      }
      withStateLabel {
        jComboBox(items).enabled(false)
      }
      withStateLabel {
        jComboBox(items).applyToComponent {
          isEditable = true
        }
      }
      withStateLabel {
        jComboBox(items)
          .enabled(false)
          .applyToComponent {
            isEditable = true
          }
      }

      group("Validation") {
        withStateLabel("Error") {
          jComboBox(items).validationOnInput {
            validate(it, true)
          }.validationOnApply {
            validate(it, true)
          }
        }

        withStateLabel("Warning") {
          jComboBox(items).validationOnInput {
            validate(it, false)
          }.validationOnApply {
            validate(it, false)
          }
        }
      }
    }

    result.registerValidators(disposable)
    result.validateAll()

    return result
  }

  private fun Row.jComboBox(items: List<String>): Cell<JComboBox<String>> {
    return cell(JComboBox(items.toTypedArray()))
  }

  private fun validate(comboBox: JComboBox<*>, isError: Boolean): ValidationInfo? {
    if (comboBox.selectedItem == "Item 2") {
      return null
    }

    return if (isError) {
      ValidationInfo("Item 2 must be selected")
    }
    else {
      ValidationInfo("Item 2 should be selected").asWarning()
    }
  }
}