// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectWizard.projectTypeStep

import com.intellij.ide.util.newProjectWizard.TemplatesGroup
import com.intellij.platform.templates.ArchivedProjectTemplate

internal class UserTemplateGroupItem(
  val template: ArchivedProjectTemplate
) : TemplateGroupItem(createTemplateGroup(template)) {

  companion object {

    private fun createTemplateGroup(template: ArchivedProjectTemplate): TemplatesGroup {
      val builder = template.createModuleBuilder()
      return TemplatesGroup(template.name, template.description, template.icon, 0, null, builder.getBuilderId(), builder)
    }
  }
}