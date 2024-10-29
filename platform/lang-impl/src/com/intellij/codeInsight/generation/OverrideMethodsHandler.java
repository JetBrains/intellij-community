// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.generation;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.lang.CodeInsightActions;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageCodeInsightActionHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;

public final class OverrideMethodsHandler implements CodeInsightActionHandler{
  @Override
  public void invoke(final @NotNull Project project, final @NotNull Editor editor, @NotNull PsiFile file) {
    if (!EditorModificationUtil.checkModificationAllowed(editor)) return;
    if (!FileDocumentManager.getInstance().requestWriting(editor.getDocument(), project)){
      return;
    }

    Language language = PsiUtilCore.getLanguageAtOffset(file, editor.getCaretModel().getOffset());
    final LanguageCodeInsightActionHandler codeInsightActionHandler = CodeInsightActions.OVERRIDE_METHOD.forLanguage(language);
    if (codeInsightActionHandler != null) {
      codeInsightActionHandler.invoke(project, editor, file);
    }
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
