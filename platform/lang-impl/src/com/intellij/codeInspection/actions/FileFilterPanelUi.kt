// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.actions

import com.intellij.find.FindBundle
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import javax.swing.JCheckBox
import javax.swing.JComboBox

internal class FileFilterPanelUi {
  fun panel(useFileMask: JCheckBox, fileMask: JComboBox<in String>): DialogPanel = panel {
    group(FindBundle.message("find.filter.file.name.group")) {
      row {
        useFileMask.text = FindBundle.message("find.filter.file.mask.checkbox")
        cell(useFileMask)
        cell(fileMask).align(AlignX.FILL)
      }
    }
  }
}