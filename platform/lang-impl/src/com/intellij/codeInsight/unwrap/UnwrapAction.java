// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.unwrap;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public class UnwrapAction extends BaseCodeInsightAction{
  public UnwrapAction() {
    super(true);
    setEnabledInModalContext(true);
  }

  @NotNull
  @Override
  protected CodeInsightActionHandler getHandler(){
    return new UnwrapHandler();
  }

  @Override
  protected boolean isValidForFile(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    return !LanguageUnwrappers.INSTANCE.allForLanguage(file.getLanguage()).isEmpty();
  }
}