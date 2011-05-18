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
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class WizardContext {
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
  private final List<Listener> myListeners = new ArrayList<Listener>();
  private StorageScheme myProjectStorageFormat = StorageScheme.DEFAULT;

  public void setProjectStorageFormat(StorageScheme format) {
    myProjectStorageFormat = format;
  }

  public interface Listener {
    void buttonsUpdateRequested();
    void nextStepRequested();
  }

  public WizardContext(Project project) {
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
    final String lastProjectLocation = GeneralSettings.getInstance().getLastProjectLocation();
    if (lastProjectLocation != null) {
      return lastProjectLocation.replace('/', File.separatorChar);
    }
    final String userHome = SystemProperties.getUserHome();
    //noinspection HardCodedStringLiteral
    return userHome.replace('/', File.separatorChar) + File.separator + ApplicationNamesInfo.getInstance().getLowercaseProductName() +
           "Projects";
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

  public void requestWizardButtonsUpdate() {
    final Listener[] listeners = myListeners.toArray(new Listener[myListeners.size()]);
    for (Listener listener : listeners) {
      listener.buttonsUpdateRequested();
    }
  }

  public void requestNextStep() {
    final Listener[] listeners = myListeners.toArray(new Listener[myListeners.size()]);
    for (Listener listener : listeners) {
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

  public ProjectBuilder getProjectBuilder() {
    return myProjectBuilder;
  }

  public void setProjectBuilder(final ProjectBuilder projectBuilder) {
    myProjectBuilder = projectBuilder;
  }

  public String getPresentationName() {
    return myProject == null ? IdeBundle.message("project.new.wizard.project.identification") : IdeBundle.message("project.new.wizard.module.identification");
  }

  public StorageScheme getProjectStorageFormat() {
    return myProjectStorageFormat;
  }
}
