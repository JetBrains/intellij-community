package com.intellij.ide.util;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;

@State(
    name = "PropertiesComponent",
    storages = {@Storage(
        id = "other",
        file = "$WORKSPACE_FILE$")})
public class ProjectPropertiesComponentImpl extends PropertiesComponentImpl implements ProjectComponent {
  public void projectClosed() {
  }

  public void projectOpened() {
  }

  public void disposeComponent() {
  }

  public void initComponent() {
  }
}
