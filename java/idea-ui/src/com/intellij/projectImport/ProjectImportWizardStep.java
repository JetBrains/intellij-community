// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.projectImport;

import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.util.io.FileUtil;

import javax.swing.*;

public abstract class ProjectImportWizardStep extends ModuleWizardStep {
  private final WizardContext myContext;

  public ProjectImportWizardStep(WizardContext context) {
    myContext = context;
  }

  @Override
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
    getWizardContext().setProjectFileDirectory(alternativePath != null && !alternativePath.isEmpty() ? alternativePath : path);
    final String global = FileUtil.toSystemIndependentName(path);
    getWizardContext().setProjectName(global.substring(global.lastIndexOf("/") + 1));
  }
}
