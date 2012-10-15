/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.util.newProjectWizard.modes;

import com.intellij.ide.util.newProjectWizard.SelectTemplateStep;
import com.intellij.ide.util.newProjectWizard.StepSequence;
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.platform.ProjectTemplate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Avdeev
 *         Date: 9/26/12
 */
public class CreateFromTemplateMode extends WizardMode {

  private SelectTemplateStep mySelectTemplateStep;

  @NotNull
  @Override
  public String getDisplayName(WizardContext context) {
    return ProjectBundle.message("project.new.wizard.from.template.title", context.getPresentationName());
  }

  @NotNull
  @Override
  public String getDescription(WizardContext context) {
    return ProjectBundle.message("project.new.wizard.from.template.description", context.getPresentationName());
  }

  @Override
  public boolean isAvailable(WizardContext context) {
    return true;
  }

  @Nullable
  @Override
  protected StepSequence createSteps(WizardContext context, @NotNull ModulesProvider modulesProvider) {
    StepSequence sequence = new StepSequence();
    mySelectTemplateStep = new SelectTemplateStep(context, sequence);
    sequence.addCommonStep(mySelectTemplateStep);
    return CreateFromScratchMode.addSteps(context, modulesProvider, this, sequence);
  }

  @Nullable
  @Override
  public ModuleBuilder getModuleBuilder() {
    final ProjectTemplate template = mySelectTemplateStep.getSelectedTemplate();
    if (template == null) {
      return null;
    }
    return template.createModuleBuilder();
  }

  @Override
  public void onChosen(boolean enabled) {
  }
}
