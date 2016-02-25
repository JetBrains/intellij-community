/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.ide.util.projectWizard;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.wizard.AbstractWizard;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.components.StorageScheme;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.platform.ProjectTemplate;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.util.List;

public class WizardContext extends UserDataHolderBase {
  /**
   * a project where the module should be added, can be null => the wizard creates a new project
   */
  @Nullable
  private final Project myProject;
  private final Disposable myDisposable;
  private String myProjectFileDirectory;
  private String myProjectName;
  private String myCompilerOutputDirectory;
  private Sdk myProjectJdk;
  private ProjectBuilder myProjectBuilder;
  private ProjectTemplate myProjectTemplate;
  private final List<Listener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private StorageScheme myProjectStorageFormat = StorageScheme.DIRECTORY_BASED;
  private boolean myNewWizard;
  private ModulesProvider myModulesProvider;
  private boolean myProjectFileDirectorySetExplicitly;
  private AbstractWizard myWizard;

  public void setProjectStorageFormat(StorageScheme format) {
    myProjectStorageFormat = format;
  }

  public boolean isNewWizard() {
    return myNewWizard;
  }

  public void setNewWizard(boolean newWizard) {
    myNewWizard = newWizard;
  }

  public ModulesProvider getModulesProvider() {
    return myModulesProvider;
  }

  public void setModulesProvider(ModulesProvider modulesProvider) {
    myModulesProvider = modulesProvider;
  }

  public Disposable getDisposable() {
    return myDisposable;
  }

  public AbstractWizard getWizard() {
    return myWizard;
  }

  public void setWizard(AbstractWizard wizard) {
    myWizard = wizard;
  }

  public interface Listener {
    void buttonsUpdateRequested();
    void nextStepRequested();
  }

  public WizardContext(@Nullable Project project, Disposable parentDisposable) {
    myProject = project;
    myDisposable = parentDisposable;
    if (myProject != null){
      myProjectJdk = ProjectRootManager.getInstance(myProject).getProjectSdk();
    }
  }

  /**
   * Use {@link #WizardContext(Project, Disposable)}.
   */
  @Deprecated
  public WizardContext(@Nullable Project project) {
    this(project, null);
  }

  @Nullable
  public Project getProject() {
    return myProject;
  }

  @NotNull
  public String getProjectFileDirectory() {
    if (myProjectFileDirectory != null) {
      return myProjectFileDirectory;
    }
    //final String lastProjectLocation = RecentProjectsManager.getInstance().getLastProjectCreationLocation();
    //if (lastProjectLocation != null) {
    //  return lastProjectLocation.replace('/', File.separatorChar);
    //}
    final String userHome = SystemProperties.getUserHome();
    //noinspection HardCodedStringLiteral
    String productName = ApplicationNamesInfo.getInstance().getLowercaseProductName();
    return userHome.replace('/', File.separatorChar) + File.separator + productName.replace(" ", "") + "Projects";
  }

  public boolean isProjectFileDirectorySet() {
    return myProjectFileDirectory != null;
  }

  public boolean isProjectFileDirectorySetExplicitly() {
    return myProjectFileDirectorySetExplicitly;
  }

  public void setProjectFileDirectory(String projectFileDirectory) {
    setProjectFileDirectory(projectFileDirectory, false);
  }

  public void setProjectFileDirectory(String projectFileDirectory, boolean explicitly) {
    myProjectFileDirectorySetExplicitly = explicitly;
    myProjectFileDirectory = projectFileDirectory;
  }

  public String getCompilerOutputDirectory() {
    return myCompilerOutputDirectory;
  }

  public void setCompilerOutputDirectory(final String compilerOutputDirectory) {
    myCompilerOutputDirectory = compilerOutputDirectory;
  }

  public String getProjectName() {
    return myProjectName;
  }

  public void setProjectName(String projectName) {
    myProjectName = projectName;
  }

  public boolean isCreatingNewProject() {
    return myProject == null;
  }

  public Icon getStepIcon() {
    return null;
  }

  public void requestWizardButtonsUpdate() {
    for (Listener listener : myListeners) {
      listener.buttonsUpdateRequested();
    }
  }

  public void requestNextStep() {
    for (Listener listener : myListeners) {
      listener.nextStepRequested();
    }
  }

  public void addContextListener(Listener listener) {
    myListeners.add(listener);
  }

  public void removeContextListener(Listener listener) {
    myListeners.remove(listener);
  }

  public void setProjectJdk(Sdk jdk) {
    myProjectJdk = jdk;
  }

  public Sdk getProjectJdk() {
    return myProjectJdk;
  }

  @Nullable
  public ProjectBuilder getProjectBuilder() {
    return myProjectBuilder;
  }

  public void setProjectBuilder(@Nullable final ProjectBuilder projectBuilder) {
    myProjectBuilder = projectBuilder;
  }

  @Nullable
  public ProjectTemplate getProjectTemplate() {
    return myProjectTemplate;
  }

  public void setProjectTemplate(ProjectTemplate projectTemplate) {
    myProjectTemplate = projectTemplate;
    setProjectBuilder(projectTemplate.createModuleBuilder());
  }

  public String getPresentationName() {
    return myProject == null ? IdeBundle.message("project.new.wizard.project.identification") : IdeBundle.message("project.new.wizard.module.identification");
  }

  public StorageScheme getProjectStorageFormat() {
    return myProjectStorageFormat;
  }
}
