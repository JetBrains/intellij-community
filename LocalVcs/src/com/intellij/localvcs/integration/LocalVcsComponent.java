package com.intellij.localvcs.integration;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class LocalVcsComponent implements ProjectComponent {
  private Project myProject;

  public LocalVcsComponent(Project p) {
    myProject = p;
  }

  public void projectOpened() {
  }

  public void projectClosed() {
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "NewLocalVcs";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }
}
