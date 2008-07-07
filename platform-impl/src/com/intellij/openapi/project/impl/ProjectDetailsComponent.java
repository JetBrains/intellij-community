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
  public void setProjectName(final String projectName) {
    myState.projectName = projectName;
  }

  public String getProjectName() {
    return myState.projectName;
  }


  public static class State {
    public String projectName;
  }

  private State myState = new State();

  public ProjectDetailsComponent(Project project) {
    myState.projectName = ((ProjectImpl)project).getDefaultName();
  }

  public State getState() {
    return myState;
  }

  public void loadState(final State state) {
    myState = state;
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
