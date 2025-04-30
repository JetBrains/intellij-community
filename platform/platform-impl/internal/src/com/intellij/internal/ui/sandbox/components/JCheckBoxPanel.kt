// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui.sandbox.components

import com.intellij.internal.ui.sandbox.UISandboxPanel
import com.intellij.internal.ui.sandbox.applyStateText
import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.selected
import javax.swing.JComponent

internal class JCheckBoxPanel : UISandboxPanel {

  override val title: String = "JCheckBox"

  override fun createContent(disposable: Disposable): JComponent {
    val panel = panel {
      group("States") {
        for (state in allCheckboxStates()) {
          row {
            val stateLabel = when {
              state.isError -> "Error"
              state.isWarning -> "Warning"
              else -> null
            }
            checkBox("")
              .selected(state.isSelected)
              .enabled(state.isEnabled)
              .validationOnInput { validate(state) }
              .validationOnApply { validate(state) }
              .applyStateText(stateLabel)
          }
        }
      }
    }
    panel.registerValidators(disposable)
    panel.validateAll()
    return panel
  }

  private fun validate(checkBoxState: CheckBoxState): ValidationInfo? {
    return when {
      checkBoxState.isError -> ValidationInfo("Checkbox should be selected")
      checkBoxState.isWarning -> ValidationInfo("Checkbox should be selected").asWarning()
      else -> null
    }
  }

  private fun allCheckboxStates(): List<CheckBoxState> {
    return listOf(
      CheckBoxState(isSelected = true, isEnabled = false, isError = false, isWarning = false),
      CheckBoxState(isSelected = true, isEnabled = true, isError = false, isWarning = false),
      CheckBoxState(isSelected = false, isEnabled = true, isError = false, isWarning = false),
      CheckBoxState(isSelected = false, isEnabled = false, isError = false, isWarning = false),
      CheckBoxState(isSelected = true, isEnabled = true, isError = true, isWarning = false),
      CheckBoxState(isSelected = true, isEnabled = true, isError = false, isWarning = true),
      CheckBoxState(isSelected = false, isEnabled = true, isError = true, isWarning = false),
      CheckBoxState(isSelected = false, isEnabled = true, isError = false, isWarning = true),
    )
  }

  private data class CheckBoxState(val isSelected: Boolean, val isEnabled: Boolean, val isError: Boolean, val isWarning: Boolean)
}