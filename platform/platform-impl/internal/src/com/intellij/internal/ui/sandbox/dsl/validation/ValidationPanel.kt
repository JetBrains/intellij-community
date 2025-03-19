// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui.sandbox.dsl.validation

import com.intellij.internal.ui.sandbox.UISandboxPanel
import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.selected
import com.intellij.ui.layout.selected
import javax.swing.JCheckBox
import javax.swing.JComponent

internal class ValidationPanel : UISandboxPanel {

  override val title: String = "Validation API"

  override fun createContent(disposable: Disposable): JComponent {
    lateinit var result: DialogPanel
    result = panel {
      lateinit var cbValidationEnabled: JCheckBox

      row {
        cbValidationEnabled = checkBox("Validation enabled")
          .selected(true)
          .component
      }

      row {
        textField()
          .comment("Must be not empty")
          .cellValidation {
            enabledIf(cbValidationEnabled.selected)
            addApplyRule("Must be not empty") { it.text.isNullOrEmpty() }
          }
      }

      row("Segmented Button:") {
        val segmentedButton = segmentedButton((1..4).toList()) { text = "Item $it" }
          .validation {
            enabledIf(cbValidationEnabled.selected)
            addApplyRule("Cannot be empty") { it.selectedItem == null }
          }
        button("Reset") {
          segmentedButton.selectedItem = null
        }
      }

      row {
        button("Validate") {
          result.validateAll()
        }
      }
    }

    result.registerValidators(disposable)
    return result
  }
}