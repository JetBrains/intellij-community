// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.codeStyle;

import com.intellij.application.options.CodeStyle;
import com.intellij.application.options.DefaultCodeStyleSettingsFacade;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public class JavaFileCodeStyleFacadeImpl extends DefaultCodeStyleSettingsFacade implements JavaFileCodeStyleFacade {

  private final JavaCodeStyleSettings myJavaSettings;

  public JavaFileCodeStyleFacadeImpl(@NotNull CodeStyleSettings settings) {
    super(settings, JavaFileType.INSTANCE);
    myJavaSettings = settings.getCustomSettings(JavaCodeStyleSettings.class);
  }

  @Override
  public int getNamesCountToUseImportOnDemand() {
    return myJavaSettings.getNamesCountToUseImportOnDemand();
  }

  @Override
  public boolean isToImportOnDemand(String qualifiedName) {
    return myJavaSettings.PACKAGES_TO_USE_IMPORT_ON_DEMAND.contains(qualifiedName);
  }

  @Override
  public boolean useFQClassNames() {
    return myJavaSettings.USE_FQ_CLASS_NAMES;
  }

  @Override
  public boolean isJavaDocLeadingAsterisksEnabled() {
    return myJavaSettings.JD_LEADING_ASTERISKS_ARE_ENABLED;
  }

  @Override
  public boolean isGenerateFinalParameters() {
    return myJavaSettings.GENERATE_FINAL_PARAMETERS;
  }

  @Override
  public boolean isGenerateFinalLocals() {
    return myJavaSettings.GENERATE_FINAL_LOCALS;
  }

  public static final class Factory implements JavaFileCodeStyleFacadeFactory {

    @Override
    public @NotNull JavaFileCodeStyleFacade createFacade(@NotNull PsiFile psiFile) {
      return new JavaFileCodeStyleFacadeImpl(CodeStyle.getSettings(psiFile));
    }
  }
}
