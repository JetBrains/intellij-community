// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic.logging

import com.intellij.diagnostic.DiagnosticBundle
import com.intellij.execution.ExecutionBundle
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.COLUMNS_SHORT
import com.intellij.ui.dsl.builder.LabelPosition
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.layout.selected

internal class LogConfigurationUi(toolbarDecorator: ToolbarDecorator) {

  lateinit var cbRedirectOutput: JBCheckBox
  lateinit var outputFile: TextFieldWithBrowseButton
  lateinit var cbShowConsoleOnStdOut: JBCheckBox
  lateinit var cbShowConsoleOnStdErr: JBCheckBox

  @JvmField
  val panel: DialogPanel = panel {
    row {
      cell(toolbarDecorator.createPanel())
        .label(DiagnosticBundle.message("log.monitor.group"), LabelPosition.TOP)
        .align(Align.FILL)
    }.resizableRow()
    row {
      cbRedirectOutput = checkBox(ExecutionBundle.message("save.output.console.to.file"))
        .gap(RightGap.SMALL)
        .component
      outputFile = cell(TextFieldWithBrowseButton())
        .align(AlignX.FILL)
        .columns(COLUMNS_SHORT)
        .enabledIf(cbRedirectOutput.selected)
        .component
    }
    row {
      cbShowConsoleOnStdOut = checkBox(ExecutionBundle.message("logs.show.console.on.stdout")).component
    }
    row {
      cbShowConsoleOnStdErr = checkBox(ExecutionBundle.message("logs.show.console.on.stderr")).component
    }
  }
}
