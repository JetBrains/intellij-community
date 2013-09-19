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
package com.intellij.ide.util.newProjectWizard;

import com.intellij.ide.highlighter.ModuleFileType;
import com.intellij.ide.highlighter.ProjectFileType;
import com.intellij.ide.util.projectWizard.ProjectBuilder;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.ide.wizard.AbstractWizard;
import com.intellij.ide.wizard.Step;
import com.intellij.openapi.components.StorageScheme;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.awt.*;
import java.io.File;

/**
 * @author Dmitry Avdeev
 *         Date: 19.09.13
 */
public abstract class AbstractProjectWizard<T extends Step> extends AbstractWizard<T> {
  protected final WizardContext myWizardContext;

  public AbstractProjectWizard(String title, Project project, String defaultPath) {
    super(title, project);
    myWizardContext = initContext(project, defaultPath);
  }

  public AbstractProjectWizard(String title, Component dialogParent) {
    super(title, dialogParent);
    myWizardContext = initContext(null, null);
  }

  private WizardContext initContext(@Nullable Project project, @Nullable String defaultPath) {
    WizardContext context = new WizardContext(project);
    if (defaultPath != null) {
      context.setProjectFileDirectory(defaultPath);
      context.setProjectName(defaultPath.substring(FileUtil.toSystemIndependentName(defaultPath).lastIndexOf("/") + 1));
    }
   return context;
  }

  @Nullable
  public static Sdk getNewProjectJdk(WizardContext context) {
    if (context.getProjectJdk() != null) {
      return context.getProjectJdk();
    }
    return getProjectSdkByDefault(context);
  }

  public static Sdk getProjectSdkByDefault(WizardContext context) {
    final Project project = context.getProject() == null ? ProjectManager.getInstance().getDefaultProject() : context.getProject();
    final Sdk projectJdk = ProjectRootManager.getInstance(project).getProjectSdk();
    if (projectJdk != null) {
      return projectJdk;
    }
    return null;
  }

  @NotNull
  public String getNewProjectFilePath() {
    if (myWizardContext.getProjectStorageFormat() == StorageScheme.DEFAULT) {
      return myWizardContext.getProjectFileDirectory() + File.separator + myWizardContext.getProjectName() + ProjectFileType.DOT_DEFAULT_EXTENSION;
    }
    else {
      return myWizardContext.getProjectFileDirectory();
    }
  }

  @NotNull
  public StorageScheme getStorageScheme() {
    return myWizardContext.getProjectStorageFormat();
  }

  public ProjectBuilder getProjectBuilder() {
    return myWizardContext.getProjectBuilder();
  }

  public String getProjectName() {
    return myWizardContext.getProjectName();
  }

  @Nullable
  public Sdk getNewProjectJdk() {
    return getNewProjectJdk(myWizardContext);
  }

  @NotNull
  public String getNewCompileOutput() {
    final String projectFilePath = myWizardContext.getProjectFileDirectory();
    @NonNls String path = myWizardContext.getCompilerOutputDirectory();
    if (path == null) {
      path = StringUtil.endsWithChar(projectFilePath, '/') ? projectFilePath + "out" : projectFilePath + "/out";
    }
    return path;
  }

  public void doNextAction() {
    super.doNextAction();
  }

  @TestOnly
  public void doOk() {
    doOKAction();
  }

  @TestOnly
  public boolean isLast() {
    return isLastStep();
  }

  @NonNls
  public String getModuleFilePath() {
    return myWizardContext.getProjectFileDirectory() + File.separator + myWizardContext.getProjectName() + ModuleFileType.DOT_DEFAULT_EXTENSION;
  }
}
