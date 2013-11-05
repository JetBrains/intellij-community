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
package com.intellij.ide.util.projectWizard;

import com.intellij.ide.GeneralSettings;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.components.StorageScheme;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.util.List;

public class WizardContext extends UserDataHolderBase {
  private static final Icon NEW_PROJECT_ICON = IconLoader.getIcon("/newprojectwizard.png");
  private static final Icon NEW_MODULE_ICON = IconLoader.getIcon("/addmodulewizard.png");
  /**
   * a project where the module should be added, can be null => the wizard creates a new project
   */
  @Nullable
  private final Project myProject;
  private String myProjectFileDirectory;
  private String myProjectName;
  private String myCompilerOutputDirectory;
  private Sdk myProjectJdk;
  private ProjectBuilder myProjectBuilder;
  private final List<Listener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private StorageScheme myProjectStorageFormat = StorageScheme.DIRECTORY_BASED;
  private boolean myNewWizard;

  public void setProjectStorageFormat(StorageScheme format) {
    myProjectStorageFormat = format;
  }

  public boolean isNewWizard() {
    return myNewWizard;
  }

  public void setNewWizard(boolean newWizard) {
    myNewWizard = newWizard;
  }

  public interface Listener {
    void buttonsUpdateRequested();
    void nextStepRequested();
  }

  public WizardContext(@Nullable Project project) {
    myProject = project;
    if (myProject != null){
      myProjectJdk = ProjectRootManager.getInstance(myProject).getProjectSdk();
    }
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
    final String lastProjectLocation = GeneralSettings.getInstance().getLastProjectCreationLocation();
    if (lastProjectLocation != null) {
      return lastProjectLocation.replace('/', File.separatorChar);
    }
    final String userHome = SystemProperties.getUserHome();
    //noinspection HardCodedStringLiteral
    String productName = ApplicationNamesInfo.getInstance().getLowercaseProductName();
    return userHome.replace('/', File.separatorChar) + File.separator + productName.replace(" ", "") + "Projects";
  }

  public boolean isProjectFileDirectorySet() {
    return myProjectFileDirectory != null;
  }

  public void setProjectFileDirectory(String projectFileDirectory) {
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
    return isCreatingNewProject() ? NEW_PROJECT_ICON : NEW_MODULE_ICON;
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

  public String getPresentationName() {
    return myProject == null ? IdeBundle.message("project.new.wizard.project.identification") : IdeBundle.message("project.new.wizard.module.identification");
  }

  public StorageScheme getProjectStorageFormat() {
    return myProjectStorageFormat;
  }
}
