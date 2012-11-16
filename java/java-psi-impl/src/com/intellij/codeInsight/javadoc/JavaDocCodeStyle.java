package com.intellij.codeInsight.javadoc;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;

public abstract class JavaDocCodeStyle {
  public static JavaDocCodeStyle getInstance(Project project) {
    return ServiceManager.getService(project, JavaDocCodeStyle.class);
  }

  public abstract boolean spaceBeforeComma();
  public abstract boolean spaceAfterComma();
}
