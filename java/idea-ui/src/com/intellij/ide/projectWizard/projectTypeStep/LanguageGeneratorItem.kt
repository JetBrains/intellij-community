// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectWizard.projectTypeStep

import com.intellij.ide.util.newProjectWizard.TemplatesGroup
import com.intellij.ide.wizard.GeneratorNewProjectWizard
import com.intellij.ide.wizard.GeneratorNewProjectWizardBuilderAdapter
import com.intellij.ide.wizard.impl.ProjectTypeListSection

internal class LanguageGeneratorItem(
  val wizard: GeneratorNewProjectWizard
) : TemplateGroupItem(createTemplateGroup(wizard)) {

  override val section: ProjectTypeListSection
    get() = ProjectTypeListSection.NEW_PROJECT

  private class NewProjectModelBuilder(wizard: GeneratorNewProjectWizard) : GeneratorNewProjectWizardBuilderAdapter(wizard)

  companion object {

    private fun createTemplateGroup(wizard: GeneratorNewProjectWizard): TemplatesGroup {
      return TemplatesGroup(NewProjectModelBuilder(wizard))
    }
  }
}
