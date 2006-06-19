/*
 * Copyright 2000-2005 JetBrains s.r.o.
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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.roots.ProjectRootManager;

import java.util.ArrayList;
import java.util.List;

public class WizardContext {
  /**
   * a project where the module should be added, can be null => the wizard creates a new project
   */
  private final Project myProject;
  private String myProjectFileDirectory;
  private String myProjectName;
  private String myCompilerOutputDirectory;
  private ProjectJdk myProjectJdk;
  private List<Listener> myListeners = new ArrayList<Listener>();

  public static interface Listener {
    void buttonsUpdateRequested();
  }

  public WizardContext(Project project) {
    myProject = project;
    if (myProject != null){
      myProjectJdk = ProjectRootManager.getInstance(myProject).getProjectJdk();
    }
  }

  public Project getProject() {
    return myProject;
  }

  public String getProjectFileDirectory() {
    return myProjectFileDirectory;
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

  public void addContextListener(Listener listener) {
    myListeners.add(listener);
  }

  public void removeContextListener(Listener listener) {
    myListeners.remove(listener);
  }

  public void setProjectJdk(ProjectJdk jdk) {
    myProjectJdk = jdk;
  }

  public ProjectJdk getProjectJdk() {
    return myProjectJdk;
  }
}
