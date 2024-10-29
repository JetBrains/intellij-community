// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.projectWizard

import com.intellij.ide.wizard.NewProjectWizardBaseStep
import com.intellij.openapi.util.io.FileUtil
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.Panel
import com.intellij.util.PathUtil
import org.jetbrains.annotations.ApiStatus
import java.io.File
import javax.swing.JComponent

@ApiStatus.Internal
class PanelBuilderSettingsStep(private val wizardContext: WizardContext,
                               private val builder: Panel,
                               private val baseStep: NewProjectWizardBaseStep) : SettingsStep {
  override fun getContext(): WizardContext = wizardContext

  override fun addSettingsField(label: String, field: JComponent) {
    with(builder) {
      row(label) {
        cell(field).align(AlignX.FILL)
      }.bottomGap(BottomGap.SMALL)
    }
  }

  override fun addSettingsComponent(component: JComponent) {
    with(builder) {
      row("") {
        cell(component).align(AlignX.FILL)
      }.bottomGap(BottomGap.SMALL)
    }
  }

  override fun addExpertPanel(panel: JComponent) {
    addSettingsComponent(panel)
  }

  override fun addExpertField(label: String, field: JComponent) {
    addSettingsField(label, field)
  }

  override fun getModuleNameLocationSettings(): ModuleNameLocationSettings {
    return object : ModuleNameLocationSettings {
      override fun getModuleName(): String = baseStep.name

      override fun setModuleName(moduleName: String) {
        baseStep.name = moduleName
      }

      override fun getModuleContentRoot(): String {
        return FileUtil.toSystemDependentName(baseStep.path).trimEnd(File.separatorChar) + File.separatorChar + baseStep.name
      }

      override fun setModuleContentRoot(path: String) {
        baseStep.path = PathUtil.getParentPath(path)
        baseStep.name = PathUtil.getFileName(path)
      }
    }
  }
}