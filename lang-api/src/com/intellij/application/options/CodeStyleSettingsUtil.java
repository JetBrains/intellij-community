package com.intellij.application.options;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;

public abstract class CodeStyleSettingsUtil {
  public static CodeStyleSettingsUtil getInstance() {
    return ServiceManager.getService(CodeStyleSettingsUtil.class);
  }

  public abstract boolean showCodeStyleSettings(Project project, Class pageToSelect);
}