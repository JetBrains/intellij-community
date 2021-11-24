// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.projectWizard

import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.EMPTY_LABEL
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import javax.swing.JComponent
import javax.swing.JTextField

class PanelBuilderSettingsStep(private val wizardContext: WizardContext, val builder: Panel) : SettingsStep {
  override fun getContext(): WizardContext = wizardContext

  override fun addSettingsField(label: String, field: JComponent) {
    with(builder) {
      row(label) {
        cell(field).horizontalAlign(HorizontalAlign.FILL)
      }.bottomGap(BottomGap.SMALL)
    }
  }

  override fun addSettingsComponent(component: JComponent) {
    with(builder) {
      row(EMPTY_LABEL) {
        cell(component).horizontalAlign(HorizontalAlign.FILL)
      }.bottomGap(BottomGap.SMALL)
    }
  }

  override fun addExpertPanel(panel: JComponent) {
    addSettingsComponent(panel)
  }

  override fun addExpertField(label: String, field: JComponent) {
    addSettingsField(label, field)
  }

  override fun getModuleNameField(): JTextField? {
    return null
  }
}