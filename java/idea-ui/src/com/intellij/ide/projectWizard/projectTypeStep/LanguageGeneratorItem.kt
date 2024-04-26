// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectWizard.projectTypeStep

import com.intellij.ide.util.newProjectWizard.TemplatesGroup
import com.intellij.ide.wizard.GeneratorNewProjectWizard
import com.intellij.ide.wizard.GeneratorNewProjectWizardBuilderAdapter
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
internal class LanguageGeneratorItem(
  val wizard: GeneratorNewProjectWizard
) : TemplateGroupItem(createTemplateGroup(wizard)) {

  private class NewProjectModelBuilder(wizard: GeneratorNewProjectWizard) : GeneratorNewProjectWizardBuilderAdapter(wizard)

  companion object {

    private fun createTemplateGroup(wizard: GeneratorNewProjectWizard): TemplatesGroup {
      return TemplatesGroup(NewProjectModelBuilder(wizard))
    }
  }
}
