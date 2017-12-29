package com.intellij.codeInsight.javadoc;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;

public class JavaDocCodeStyleImpl extends JavaDocCodeStyle {
  private final Project myProject;

  public JavaDocCodeStyleImpl(Project project) {
    myProject = project;
  }

  @Override
  public boolean spaceBeforeComma() {
    CommonCodeStyleSettings styleSettings = CodeStyleSettingsManager.getSettings(myProject).getCommonSettings(JavaLanguage.INSTANCE);
    return styleSettings.SPACE_BEFORE_COMMA;
  }

  @Override
  public boolean spaceAfterComma() {
    CommonCodeStyleSettings styleSettings = CodeStyleSettingsManager.getSettings(myProject).getCommonSettings(JavaLanguage.INSTANCE);
    return styleSettings.SPACE_AFTER_COMMA;
  }
}
