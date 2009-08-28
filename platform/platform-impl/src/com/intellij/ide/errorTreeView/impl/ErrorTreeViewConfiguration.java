package com.intellij.ide.errorTreeView.impl;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;


@State(
  name = "ErrorTreeViewConfiguration",
  storages = {
    @Storage(
      id ="other",
      file = "$WORKSPACE_FILE$"
    )}
)
public class ErrorTreeViewConfiguration implements PersistentStateComponent<ErrorTreeViewConfiguration> {
  public boolean IS_AUTOSCROLL_TO_SOURCE = false;
  public boolean HIDE_WARNINGS = false;

  public static ErrorTreeViewConfiguration getInstance(Project project) {
    return ServiceManager.getService(project, ErrorTreeViewConfiguration.class);
  }

  public boolean isAutoscrollToSource() {
    return IS_AUTOSCROLL_TO_SOURCE;
  }

  public void setAutoscrollToSource(boolean autoscroll) {
    IS_AUTOSCROLL_TO_SOURCE = autoscroll;
  }

  public boolean isHideWarnings() {
    return HIDE_WARNINGS;
  }

  public void setHideWarnings(boolean value) {
    HIDE_WARNINGS = value;
  }

  public ErrorTreeViewConfiguration getState() {
    return this;
  }

  public void loadState(final ErrorTreeViewConfiguration state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}
