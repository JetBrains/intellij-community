/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
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
    for (int idx = 0; idx < listeners.length; idx++) {
      listeners[idx].buttonsUpdateRequested();
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
