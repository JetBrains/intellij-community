// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.console

import com.intellij.execution.ExecutionBundle
import com.intellij.execution.impl.ConsoleBuffer
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.vfs.limits.FileSizeLimit.Companion.getDefaultContentLoadLimit
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.layout.selected
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.event.DocumentEvent

@ApiStatus.Internal
internal class ConsoleConfigurableUI(
  isEditFoldingsOnly: Boolean, positivePanel: JComponent, negativePanel: JComponent,
  appendConfigurables: (Panel) -> Unit,
) {

  lateinit var cbUseSoftWrapsAtConsole: JBCheckBox
  lateinit var commandsHistoryLimitField: JBTextField
  lateinit var cbOverrideConsoleCycleBufferSize: JBCheckBox
  lateinit var consoleCycleBufferSizeField: JBTextField
  lateinit var consoleBufferSizeWarningLabel: JLabel

  @JvmField
  val encodingComboBox = ConsoleEncodingComboBox()

  val content = panel {
    rowsRange {
      row {
        cbUseSoftWrapsAtConsole = checkBox(ApplicationBundle.message("checkbox.use.soft.wraps.at.console"))
          .component
      }
      row(ApplicationBundle.message("editbox.console.history.limit")) {
        commandsHistoryLimitField = textField()
          .columns(COLUMNS_TINY)
          .component
      }

      row {
        cbOverrideConsoleCycleBufferSize = checkBox(ApplicationBundle.message("checkbox.override.console.cycle.buffer.size", (ConsoleBuffer.getLegacyCycleBufferSize() / 1024).toString()))
          .gap(RightGap.SMALL)
          .component
        consoleCycleBufferSizeField = textField()
          .columns(COLUMNS_TINY)
          .gap(RightGap.SMALL)
          .enabledIf(cbOverrideConsoleCycleBufferSize.selected)
          .applyToComponent {
            document.addDocumentListener(object : DocumentAdapter() {
              override fun textChanged(e: DocumentEvent) {
                updateWarningLabel()
              }
            })
          }.component
        label(ExecutionBundle.message("settings.console.kb"))
        consoleBufferSizeWarningLabel = label("")
          .visibleIf(cbOverrideConsoleCycleBufferSize.selected)
          .applyToComponent {
            foreground = JBColor.red
          }.component
      }.visible(ConsoleBuffer.useCycleBuffer())

      row(ApplicationBundle.message("combobox.console.default.encoding.label")) {
        cell(encodingComboBox)
      }.layout(RowLayout.INDEPENDENT)
    }.visible(!isEditFoldingsOnly)

    row {
      cell(positivePanel)
        .align(AlignX.FILL)
    }

    row {
      cell(negativePanel)
        .align(AlignX.FILL)
    }

    appendConfigurables(this)
  }

  private fun updateWarningLabel() {
    val warning = try {
      val value = consoleCycleBufferSizeField.getText().trim { it <= ' ' }.toInt()
      if (value <= 0) {
        ApplicationBundle.message("checkbox.override.console.cycle.buffer.size.warning.unlimited")
      }
      else if (value > getDefaultContentLoadLimit() / 1024) {
        ApplicationBundle.message("checkbox.override.console.cycle.buffer.size.warning.too.large")
      }
      else {
        ""
      }
    }
    catch (_: NumberFormatException) {
      ""
    }
    consoleBufferSizeWarningLabel.setText(warning)
  }
}