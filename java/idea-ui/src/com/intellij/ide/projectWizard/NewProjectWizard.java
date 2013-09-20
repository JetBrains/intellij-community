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
import com.intellij.ide.util.newProjectWizard.ProjectNameStep;
import com.intellij.ide.wizard.Step;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Dmitry Avdeev
 *         Date: 04.09.13
 */
public class NewProjectWizard extends AbstractProjectWizard<Step> {

  private final ProjectNameStep myProjectNameStep;

  public NewProjectWizard(@Nullable Project project, String defaultPath) {
    super("New Project", null, defaultPath);
    addStep(new ProjectTypeStep(myWizardContext, getDisposable()));
    myProjectNameStep = new ProjectNameStep(myWizardContext, null) {
      @Override
      public Icon getIcon() {
        return null;
      }
    };
    addStep(myProjectNameStep);
    Disposer.register(getDisposable(), myProjectNameStep);
    init();
  }

  @Nullable
  @Override
  protected String getHelpID() {
    return null;
  }

  @Nullable
  @Override
  protected String getDimensionServiceKey() {
    return "new project wizard";
  }

  @Override
  protected void doOKAction() {
    myProjectNameStep.updateDataModel();
    super.doOKAction();
  }
}
