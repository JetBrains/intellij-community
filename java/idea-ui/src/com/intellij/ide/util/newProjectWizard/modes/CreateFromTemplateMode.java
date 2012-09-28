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

import com.intellij.ide.util.newProjectWizard.ProjectNameStep;
import com.intellij.ide.util.newProjectWizard.SelectTemplateStep;
import com.intellij.ide.util.newProjectWizard.StepSequence;
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.ide.util.projectWizard.ProjectBuilder;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.platform.ProjectTemplate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Dmitry Avdeev
 *         Date: 9/26/12
 */
public class CreateFromTemplateMode extends WizardMode {

  private final SelectTemplateStep mySelectTemplateStep = new SelectTemplateStep();

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
    return new StepSequence(mySelectTemplateStep, new ProjectNameStep(context, this));
  }

  @Nullable
  @Override
  public ProjectBuilder getModuleBuilder() {
    final ProjectTemplate template = mySelectTemplateStep.getSelectedTemplate();
    if (template == null) {
      return null;
    }
    final ModuleBuilder builder = template.getModuleType().createModuleBuilder();

    return new ProjectBuilder() {
      @Nullable
      @Override
      public List<Module> commit(Project project, ModifiableModuleModel model, ModulesProvider modulesProvider) {
        List<Module> modules = builder.commit(project, model, modulesProvider);
        if (modules != null && !modules.isEmpty()) {
          template.generateProject(modules.get(0));
        }
        return modules;
      }
    };
  }

  @Override
  public void onChosen(boolean enabled) {
  }
}
