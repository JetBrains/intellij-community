package com.intellij.codeInsight.javadoc;

import com.intellij.openapi.project.Project;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;

public class JavaDocCodeStyleImpl extends JavaDocCodeStyle {
  private final Project myProject;

  public JavaDocCodeStyleImpl(Project project) {
    myProject = project;
  }

  @Override
  public boolean spaceBeforeComma() {
    CodeStyleSettings styleSettings = CodeStyleSettingsManager.getSettings(myProject);
    return styleSettings.SPACE_BEFORE_COMMA;
  }

  @Override
  public boolean spaceAfterComma() {
    CodeStyleSettings styleSettings = CodeStyleSettingsManager.getSettings(myProject);
    return styleSettings.SPACE_AFTER_COMMA;
  }
}
