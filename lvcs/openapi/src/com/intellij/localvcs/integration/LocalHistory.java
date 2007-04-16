package com.intellij.localvcs.integration;

import com.intellij.openapi.components.SettingsSavingComponent;
import com.intellij.openapi.project.Project;

public abstract class LocalHistory implements SettingsSavingComponent {
  // make it private
  public static LocalHistory getInstance(Project p) {
    return p.getComponent(LocalHistory.class);
  }

  public static LocalHistoryAction startAction(Project p, String name) {
    return getInstance(p).startAction(name);
  }

  public static void putLabel(Project p, String name) {
    getInstance(p).putLabel(name);
  }

  public static void putLabel(Project p, String path, String name) {
    getInstance(p).putLabel(path, name);
  }

  public static boolean isEnabled(Project p) {
    return getInstance(p).isEnabled();
  }

  protected abstract LocalHistoryAction startAction(String name);

  protected abstract void putLabel(String name);

  protected abstract void putLabel(String path, String name);

  protected abstract boolean isEnabled();
}
