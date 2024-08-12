// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.generation.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.codeInsight.generation.surroundWith.SurroundWithHandler;
import com.intellij.codeInsight.template.TemplateActionContext;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageSurrounders;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiBinaryFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;

public final class SurroundWithAction extends BaseCodeInsightAction {
  public SurroundWithAction() {
    setEnabledInModalContext(true);
  }

  @Override
  protected @NotNull CodeInsightActionHandler getHandler() {
    return new SurroundWithHandler();
  }

  @Override
  protected boolean isValidForFile(@NotNull Project project, @NotNull Editor editor, final @NotNull PsiFile file) {
    final Language language = file.getLanguage();
    if (!LanguageSurrounders.INSTANCE.allForLanguage(language).isEmpty()) {
      return true;
    }
    final PsiFile baseFile = PsiUtilCore.getTemplateLanguageFile(file);
    if (baseFile != null && baseFile != file && !LanguageSurrounders.INSTANCE.allForLanguage(baseFile.getLanguage()).isEmpty()) {
      return true;
    }

    if (file instanceof PsiBinaryFile) {
      return true;
    }

    if (!TemplateManagerImpl.listApplicableTemplates(TemplateActionContext.surrounding(file, editor)).isEmpty()) {
      return true;
    }

    return false;
  }
}