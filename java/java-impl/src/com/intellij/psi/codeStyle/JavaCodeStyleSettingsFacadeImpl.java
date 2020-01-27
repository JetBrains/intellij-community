// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.codeStyle;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public final class JavaCodeStyleSettingsFacadeImpl extends JavaCodeStyleSettingsFacade {
  private final CodeStyleSettingsManager myManager;

  public JavaCodeStyleSettingsFacadeImpl(@NotNull Project project) {
    myManager = CodeStyleSettingsManager.getInstance(project);
  }

  @Override
  public int getNamesCountToUseImportOnDemand() {
    return myManager.getCurrentSettings().getCustomSettings(JavaCodeStyleSettings.class).NAMES_COUNT_TO_USE_IMPORT_ON_DEMAND;
  }

  @Override
  public boolean isToImportInDemand(String qualifiedName) {
    return myManager.getCurrentSettings().getCustomSettings(JavaCodeStyleSettings.class).PACKAGES_TO_USE_IMPORT_ON_DEMAND.contains(qualifiedName);
  }

  @Override
  public boolean useFQClassNames() {
    return myManager.getCurrentSettings().getCustomSettings(JavaCodeStyleSettings.class).USE_FQ_CLASS_NAMES;
  }

  @Override
  public boolean isJavaDocLeadingAsterisksEnabled() {
    return myManager.getCurrentSettings().getCustomSettings(JavaCodeStyleSettings.class).JD_LEADING_ASTERISKS_ARE_ENABLED;
  }

  @Override
  public int getIndentSize() {
    return myManager.getCurrentSettings().getIndentSize(JavaFileType.INSTANCE);
  }

  @Override
  public boolean isGenerateFinalParameters() {
    return myManager.getCurrentSettings().getCustomSettings(JavaCodeStyleSettings.class).GENERATE_FINAL_PARAMETERS;
  }

  @Override
  public boolean isGenerateFinalLocals() {
    return myManager.getCurrentSettings().getCustomSettings(JavaCodeStyleSettings.class).GENERATE_FINAL_LOCALS;
  }
}
