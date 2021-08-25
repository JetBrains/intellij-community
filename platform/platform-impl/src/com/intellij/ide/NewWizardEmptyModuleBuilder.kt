// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide

import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.project.Project
import com.intellij.util.ui.EmptyIcon
import javax.swing.Icon

class NewWizardEmptyModuleBuilder : NewWizardModuleBuilder<EmptySettings>() {
  override val step = EmptyModuleStep()

  override fun getModuleType() = NewWizardEmptyModuleType.INSTANCE
  override fun getGroupName() = DEFAULT_GROUP
  override fun getNodeIcon(): Icon = EmptyIcon.ICON_16

  override fun setupProject(project: Project, context: WizardContext) {
    step.settings
  }
}