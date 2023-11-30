// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.codeStyle.arrangement.additional

import com.intellij.openapi.application.ApplicationBundle
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComboBox

class ForceArrangementPanel {

  private val forceRearrangeValues = mapOf(
    CommonCodeStyleSettings.REARRANGE_ACCORDIND_TO_DIALOG to ApplicationBundle.message(
      "arrangement.settings.additional.force.rearrange.according.to.dialog"),
    CommonCodeStyleSettings.REARRANGE_ALWAYS to ApplicationBundle.message("arrangement.settings.additional.force.rearrange.always"),
    CommonCodeStyleSettings.REARRANGE_NEVER to ApplicationBundle.message("arrangement.settings.additional.force.rearrange.never"))
  private lateinit var cbForceRearrange: JComboBox<Int>

  var forceRearrangeMode: Int
    get() = cbForceRearrange.selectedItem as Int
    set(value) = cbForceRearrange.setSelectedItem(value)

  val panel = panel {
    group(ApplicationBundle.message("arrangement.settings.additional.title")) {
      row(ApplicationBundle.message("arrangement.settings.additional.force.combobox.name")) {
        cbForceRearrange = comboBox(forceRearrangeValues.keys, SimpleListCellRenderer.create("") { forceRearrangeValues[it] }).component
      }
    }
  }
}
