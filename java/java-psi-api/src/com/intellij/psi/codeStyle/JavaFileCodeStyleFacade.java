// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.codeStyle;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public interface JavaFileCodeStyleFacade extends CodeStyleSettingsFacade {
  int getNamesCountToUseImportOnDemand();

  boolean isToImportOnDemand(String qualifiedName);

  boolean useFQClassNames();

  boolean isJavaDocLeadingAsterisksEnabled();

  boolean isGenerateFinalParameters();

  boolean isGenerateFinalLocals();

  static JavaFileCodeStyleFacade forContext(@NotNull PsiFile psiFile) {
    JavaFileCodeStyleFacadeFactory factory = ServiceManager.getService(JavaFileCodeStyleFacadeFactory.class);
    return factory.createFacade(psiFile);
  }
}
