// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide

import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.module.ModuleTypeManager
import com.intellij.ui.UIBundle
import com.intellij.util.ui.EmptyIcon
import javax.swing.Icon

class NewProjectModuleType : ModuleType<NewProjectModuleBuilder>("newWizard.newProjectType") {
  override fun createModuleBuilder() = NewProjectModuleBuilder()
  override fun getDescription() = UIBundle.message("label.project.wizard.new.project.generator.description")
  override fun getName() = UIBundle.message("label.project.wizard.new.project.generator.name")
  override fun getNodeIcon(isOpened: Boolean): Icon = EmptyIcon.ICON_0

  companion object {
    val INSTANCE: ModuleType<*> = ModuleTypeManager.getInstance().findByID("newWizard.newProjectType") as ModuleType
  }
}