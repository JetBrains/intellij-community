package com.intellij.ide.errorTreeView.impl;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

public class ErrorTreeViewConfiguration implements JDOMExternalizable, ProjectComponent {
  public boolean IS_AUTOSCROLL_TO_SOURCE = false;
  public boolean HIDE_WARNINGS = false;

  public static ErrorTreeViewConfiguration getInstance(Project project) {
    return project.getComponent(ErrorTreeViewConfiguration.class);
  }


  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
  }

  public void projectOpened() {
  }

  public void projectClosed() {
  }

  public void initComponent() { }

  public void disposeComponent() {
  }

  @NotNull
  public String getComponentName() {
    return "ErrorTreeViewConfiguration";
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


}
