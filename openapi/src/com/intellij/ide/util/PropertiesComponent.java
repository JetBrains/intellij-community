package com.intellij.ide.util;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;

public abstract class PropertiesComponent {

  public static PropertiesComponent getInstance(Project project) {
    return project.getComponent(PropertiesComponent.class);
  }

  public static PropertiesComponent getInstance() {
    return ApplicationManager.getApplication().getComponent(PropertiesComponent.class);
  }

  public abstract String getValue(String name);

  public abstract void setValue(String name, String value);

  public abstract boolean isTrueValue(String name);
}
