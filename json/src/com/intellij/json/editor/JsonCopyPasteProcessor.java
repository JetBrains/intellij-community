// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.editor;

import com.intellij.codeInsight.editorActions.CopyPastePreProcessor;
import com.intellij.json.psi.JsonFile;
import com.intellij.json.psi.JsonStringLiteral;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RawText;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class JsonCopyPasteProcessor implements CopyPastePreProcessor {
  @Override
  public @Nullable String preprocessOnCopy(PsiFile file, int[] startOffsets, int[] endOffsets, String text) {
    if (!JsonEditorOptions.getInstance().ESCAPE_PASTED_TEXT) {
      return null;
    }
    if (!isSupportedFile(file) || startOffsets.length > 1 || endOffsets.length > 1) {
      return null;
    }
    final int selectionStart = startOffsets[0];
    final int selectionEnd = endOffsets[0];
    final JsonStringLiteral literalExpression = getSingleElementFromSelectionOrNull(file, selectionStart, selectionEnd);

    if (literalExpression == null) {
      return null;
    }

    return StringUtil.unescapeStringCharacters(StringUtil.replaceUnicodeEscapeSequences(text));
  }

  private static @Nullable JsonStringLiteral getSingleElementFromSelectionOrNull(PsiFile file, int start, int end) {
    final PsiElement element = file.findElementAt(start);
    final JsonStringLiteral literalExpression = PsiTreeUtil.getParentOfType(element, JsonStringLiteral.class);
    if (literalExpression == null) return null;
    TextRange textRange = literalExpression.getTextRange();
    if (start <= textRange.getStartOffset() || end >= textRange.getEndOffset()) return null;
    String text = literalExpression.getText();
    if (!text.startsWith("\"") || !text.endsWith("\"")) return null;
    return literalExpression;
  }

  @Override
  public @NotNull String preprocessOnPaste(Project project, PsiFile file, Editor editor, String text, RawText rawText) {
    if (!JsonEditorOptions.getInstance().ESCAPE_PASTED_TEXT) {
      return text;
    }
    if (!isSupportedFile(file)) {
      return text;
    }

    final SelectionModel selectionModel = editor.getSelectionModel();
    final int selectionStart = selectionModel.getSelectionStart();
    final int selectionEnd = selectionModel.getSelectionEnd();

    final JsonStringLiteral literalExpression = getSingleElementFromSelectionOrNull(file, selectionStart, selectionEnd);
    if (literalExpression == null) {
      return text;
    }

    return StringUtil.escapeStringCharacters(text);
  }

  private static boolean isSupportedFile(PsiFile file) {
    return file instanceof JsonFile && file.isPhysical();
  }

  @Override
  public boolean requiresAllDocumentsToBeCommitted(@NotNull Editor editor, @NotNull Project project) {
    return false;
  }
}
