/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.ide.projectWizard;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.newProjectWizard.AbstractProjectWizard;
import com.intellij.ide.util.newProjectWizard.StepSequence;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * @author Dmitry Avdeev
 */
public class NewProjectWizard extends AbstractProjectWizard {

  private final StepSequence mySequence = new StepSequence();

  public NewProjectWizard(@Nullable Project project, @NotNull ModulesProvider modulesProvider, @Nullable String defaultPath) {
    super(project == null ? IdeBundle.message("title.new.project") : IdeBundle.message("title.add.module"), project, defaultPath);
    init(modulesProvider);
  }

  public NewProjectWizard(Project project, Component dialogParent, ModulesProvider modulesProvider) {
    super(IdeBundle.message("title.add.module"), project, dialogParent);
    init(modulesProvider);
  }

  protected void init(@NotNull ModulesProvider modulesProvider) {
    myWizardContext.setNewWizard(true);
    myWizardContext.setModulesProvider(modulesProvider);
    ProjectTypeStep projectTypeStep = new ProjectTypeStep(myWizardContext, this, modulesProvider);
    Disposer.register(getDisposable(), projectTypeStep);
    mySequence.addCommonStep(projectTypeStep);
    ChooseTemplateStep chooseTemplateStep = new ChooseTemplateStep(myWizardContext, projectTypeStep);
    mySequence.addCommonStep(chooseTemplateStep);
    mySequence.addCommonFinishingStep(new ProjectSettingsStep(myWizardContext), null);
    for (ModuleWizardStep step : mySequence.getAllSteps()) {
      addStep(step);
    }
    if (myWizardContext.isCreatingNewProject()) {
      projectTypeStep.loadRemoteTemplates(chooseTemplateStep);
    }
    super.init();
  }

  @Nullable
  @Override
  protected String getDimensionServiceKey() {
    return "new project wizard";
  }

  @Override
  public StepSequence getSequence() {
    return mySequence;
  }
}
