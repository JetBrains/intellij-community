// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.codeStyle.lineIndent;

import com.intellij.formatting.FormattingMode;
import com.intellij.lang.Language;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.lineIndent.LineIndentProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Formatter-based line indent provider which calculates indent using formatting model.
 */
public class FormatterBasedLineIndentProvider implements LineIndentProvider {
  @Override
  public @Nullable String getLineIndent(@NotNull Project project, @NotNull Editor editor, Language language, int offset) {
    Document document = editor.getDocument();
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
    documentManager.commitDocument(document);
    PsiFile file = documentManager.getPsiFile(document);
    if (file == null) return "";
    return CodeStyleManager.getInstance(project).getLineIndent(file, offset, FormattingMode.ADJUST_INDENT_ON_ENTER);
  }

  @Override
  public boolean isSuitableFor(@Nullable Language language) {
    return true;
  }
}
