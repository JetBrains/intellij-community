// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.lookup;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.TailType;
import com.intellij.lang.Language;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;

public class CommaTailType extends TailType {
  public static final TailType INSTANCE = new CommaTailType();

  @Override
  public int processTail(final Editor editor, int tailOffset) {
    CommonCodeStyleSettings styleSettings = getLocalCodeStyleSettings(editor, tailOffset);
    if (styleSettings.SPACE_BEFORE_COMMA) tailOffset = insertChar(editor, tailOffset, ' ');
    tailOffset = insertChar(editor, tailOffset, ',');
    if (styleSettings.SPACE_AFTER_COMMA) tailOffset = insertChar(editor, tailOffset, ' ');
    return tailOffset;
  }

  public String toString() {
    return "COMMA";
  }

  public static CommonCodeStyleSettings getLocalCodeStyleSettings(Editor editor, int tailOffset) {
    final PsiFile psiFile = getFile(editor);
    Language language = PsiUtilCore.getLanguageAtOffset(psiFile, tailOffset);
    return CodeStyle.getLanguageSettings(psiFile, language);
  }

  @NotNull
  private static PsiFile getFile(Editor editor) {
    Project project = editor.getProject();
    assert project != null;
    PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    assert psiFile != null;
    return psiFile;
  }
}
