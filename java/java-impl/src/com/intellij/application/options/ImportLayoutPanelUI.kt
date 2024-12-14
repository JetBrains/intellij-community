// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options

import com.intellij.java.JavaBundle
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import javax.swing.JPanel

internal class ImportLayoutPanelUI(staticImportsCb: JBCheckBox, additionalCheckBoxes: List<JBCheckBox?>, importLayoutPanel: JPanel) {
  val panel = panel {
    group(JavaBundle.message("title.import.layout")) {
      for (box in additionalCheckBoxes) {
        if (box != null) row { cell(box) }
      }
      row { cell(staticImportsCb) }
      row { cell(importLayoutPanel).align(Align.FILL) }.resizableRow()
    }.resizableRow()
  }
}