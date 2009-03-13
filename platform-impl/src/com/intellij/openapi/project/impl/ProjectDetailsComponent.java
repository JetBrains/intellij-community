package com.intellij.openapi.project.impl;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;


@State(
  name = "ProjectDetails",
  storages = {
    @Storage(
        id="other",
        file = "$PROJECT_FILE$"
    )}
)
public class ProjectDetailsComponent implements PersistentStateComponent<ProjectDetailsComponent.State>, ProjectComponent {
  private final Project myProject;

  public void setProjectName(final String projectName) {
    myState.projectName = projectName;
  }

  public String getProjectName() {
    return myProject.isDefault() ? ((ProjectImpl)myProject).getDefaultName() : myState.projectName;
  }

  public static class State {
    public String projectName;
  }

  private State myState = new State();

  public ProjectDetailsComponent(Project project) {
    myProject = project;
    myState.projectName = ((ProjectImpl)myProject).getDefaultName();
  }

  public State getState() {
    if (!myProject.isDefault()) {
      return myState;
    }
    else {
      return null;
    }
  }

  public void loadState(final State state) {
    myState = state;
    if (!myProject.isDefault() && ProjectImpl.TEMPLATE_PROJECT_NAME.equals(myState.projectName) && myProject instanceof ProjectImpl){
      myState.projectName = ((ProjectImpl)myProject).getDefaultName();
    }
  }

  public void projectOpened() {

  }

  public void projectClosed() {
  }

  @NotNull
  public String getComponentName() {
    return "ProjectDetails";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  public static ProjectDetailsComponent getInstance(Project project) {
    return project.getComponent(ProjectDetailsComponent.class);
  }
}
