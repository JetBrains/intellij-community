package com.intellij.localvcs.integration;

import com.intellij.openapi.components.SettingsSavingComponent;
import com.intellij.openapi.project.Project;

public abstract class LocalHistory implements SettingsSavingComponent {
  public static LocalHistory getInstance(Project p) {
    return p.getComponent(LocalHistory.class);
  }

  public static LocalHistoryAction startAction(Project p, String name) {
    return getInstance(p).startAction(name);
  }

  protected abstract LocalHistoryAction startAction(String name);

  public abstract boolean isEnabled();
}
