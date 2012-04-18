/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.projectImport;

import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.util.io.FileUtil;

import javax.swing.*;

/**
 * @author Vladislav.Kaznacheev
 */
public abstract class ProjectImportWizardStep extends ModuleWizardStep {
  private final WizardContext myContext;

  public ProjectImportWizardStep(WizardContext context) {
    myContext = context;
  }

  public Icon getIcon() {
    return myContext.getStepIcon();
  }
  
  protected ProjectImportBuilder getBuilder() {
    return (ProjectImportBuilder)myContext.getProjectBuilder();
  }

  protected WizardContext getWizardContext() {
    return myContext;
  }

  protected void suggestProjectNameAndPath(final String alternativePath, final String path) {
    getWizardContext().setProjectFileDirectory(alternativePath != null && alternativePath.length() > 0 ? alternativePath : path);
    final String global = FileUtil.toSystemIndependentName(path);
    getWizardContext().setProjectName(global.substring(global.lastIndexOf("/") + 1));
  }
}
