/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.editorActions;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RawText;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class StringLiteralCopyPasteProcessor implements CopyPastePreProcessor {
  public String preprocessOnCopy(final PsiFile file, final int[] startOffsets, final int[] endOffsets, final String text) {
    boolean isLiteral = true;
    for (int i = 0; i < startOffsets.length && isLiteral; i++) {
      if (findLiteralTokenType(file, startOffsets[i], endOffsets[i]) == null) {
        isLiteral = false;
      }
    }

    return isLiteral ? StringUtil.unescapeStringCharacters(text) : null;
  }

  public String preprocessOnPaste(final Project project, final PsiFile file, final Editor editor, String text, final RawText rawText) {
    final Document document = editor.getDocument();
    PsiDocumentManager.getInstance(project).commitDocument(document);
    final SelectionModel selectionModel = editor.getSelectionModel();

    // pastes in block selection mode (column mode) are not handled by a CopyPasteProcessor
    final int selectionStart = selectionModel.getSelectionStart();
    final int selectionEnd = selectionModel.getSelectionEnd();
    IElementType tokenType = findLiteralTokenType(file, selectionStart, selectionEnd);

    if (tokenType == JavaTokenType.STRING_LITERAL) {
      if (rawText != null && rawText.rawText != null) return rawText.rawText; // Copied from the string literal. Copy as is.

      StringBuilder buffer = new StringBuilder(text.length());
      CodeStyleSettings codeStyleSettings = CodeStyleSettingsManager.getSettings(project);
      @NonNls String breaker = codeStyleSettings.BINARY_OPERATION_SIGN_ON_NEXT_LINE ? "\\n\"\n+ \"" : "\\n\" +\n\"";
      final String[] lines = LineTokenizer.tokenize(text.toCharArray(), false, true);
      for (int i = 0; i < lines.length; i++) {
        String line = lines[i];
        buffer.append(StringUtil.escapeStringCharacters(line));
        if (i != lines.length - 1) buffer.append(breaker);
      }
      text = buffer.toString();
    }
    else if (tokenType == JavaTokenType.CHARACTER_LITERAL) {
      if (rawText != null && rawText.rawText != null) return rawText.rawText; // Copied from the string literal. Copy as is.
      return escapeCharCharacters(text);
    }
    return text;
  }

  @Nullable
  private static IElementType findLiteralTokenType(PsiFile file, int selectionStart, int selectionEnd) {
    final PsiElement elementAtSelectionStart = file.findElementAt(selectionStart);
    if (!(elementAtSelectionStart instanceof PsiJavaToken)) {
      return null;
    }
    final IElementType tokenType = ((PsiJavaToken)elementAtSelectionStart).getTokenType();
    if ((tokenType != JavaTokenType.STRING_LITERAL && tokenType != JavaTokenType.CHARACTER_LITERAL)) {
      return null;
    }

    if (elementAtSelectionStart.getTextRange().getEndOffset() < selectionEnd) {
      final PsiElement elementAtSelectionEnd = file.findElementAt(selectionEnd);
      if (!(elementAtSelectionEnd instanceof PsiJavaToken)) {
        return null;
      }
      if (((PsiJavaToken)elementAtSelectionEnd).getTokenType() == tokenType) {
        return tokenType;
      }
    }
    
    final TextRange textRange = elementAtSelectionStart.getTextRange();
    if (selectionStart <= textRange.getStartOffset() || selectionEnd >= textRange.getEndOffset()) {
      return null;
    }
    return tokenType;
  }

  @Nullable
  private static PsiElement next(final @NotNull PsiElement element) {
    for (PsiElement anchor = element; anchor != null; anchor = anchor.getParent()) {
      final PsiElement result = element.getNextSibling();
      if (result != null) {
        return result;
      }
    }
    return null;
  }
  
  @NotNull
  public static String escapeCharCharacters(@NotNull String s) {
    StringBuilder buffer = new StringBuilder();
    StringUtil.escapeStringCharacters(s.length(), s, "\'", buffer);
    return buffer.toString();
  }
}
