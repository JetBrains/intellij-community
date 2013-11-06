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

import com.intellij.ide.util.newProjectWizard.AbstractProjectWizard;
import com.intellij.ide.util.newProjectWizard.StepSequence;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Avdeev
 *         Date: 04.09.13
 */
public class NewProjectWizard extends AbstractProjectWizard {

  private final StepSequence mySequence;

  public NewProjectWizard(@Nullable Project project, @NotNull ModulesProvider modulesProvider, @Nullable String defaultPath) {
    super("New Project", project, defaultPath);
    myWizardContext.setNewWizard(true);
    mySequence = new StepSequence();
    mySequence.addCommonStep(new ProjectTypeStep(myWizardContext, this, modulesProvider));
    mySequence.addCommonFinishingStep(new ProjectSettingsStep(myWizardContext), null);
    for (ModuleWizardStep step : mySequence.getAllSteps()) {
      addStep(step);
    }
    init();
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
