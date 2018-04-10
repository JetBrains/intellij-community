/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author AG
 * @author yole
 */
public class SelectionQuotingTypedHandler extends TypedHandlerDelegate {
  public static final ExtensionPointName<DequotingFilter> EP_NAME =
    ExtensionPointName.create("com.intellij.selectionDequotingFilter");

  @NotNull
  @Override
  public Result beforeSelectionRemoved(char c, @NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    SelectionModel selectionModel = editor.getSelectionModel();
    if(CodeInsightSettings.getInstance().SURROUND_SELECTION_ON_QUOTE_TYPED && selectionModel.hasSelection() && isDelimiter(c)) {
      String selectedText = selectionModel.getSelectedText();
      if (!StringUtil.isEmpty(selectedText)) {
        final int selectionStart = selectionModel.getSelectionStart();
        final int selectionEnd = selectionModel.getSelectionEnd();
        if (selectedText.length() > 1) {
          final char firstChar = selectedText.charAt(0);
          final char lastChar = selectedText.charAt(selectedText.length() - 1);
          if (isSimilarDelimiters(firstChar, c) && lastChar == getMatchingDelimiter(firstChar) &&
              (isQuote(firstChar) || firstChar != c) && !shouldSkipReplacementOfQuotesOrBraces(file, editor, selectedText, c) &&
              selectedText.indexOf(lastChar, 1) == selectedText.length() - 1) {
            selectedText = selectedText.substring(1, selectedText.length() - 1);
          }
        }
        final int caretOffset = selectionModel.getSelectionStart();
        final char c2 = getMatchingDelimiter(c);
        final String newText = String.valueOf(c) + selectedText + c2;
        boolean ltrSelection = selectionModel.getLeadSelectionOffset() != selectionModel.getSelectionEnd();
        boolean restoreStickySelection = editor instanceof EditorEx && ((EditorEx)editor).isStickySelection();
        selectionModel.removeSelection();
        editor.getDocument().replaceString(selectionStart, selectionEnd, newText);
        TextRange replacedTextRange = Registry.is("editor.smarterSelectionQuoting")
                            ? new TextRange(caretOffset + 1, caretOffset + newText.length() - 1)
                            : new TextRange(caretOffset, caretOffset + newText.length());
        // selection is removed here
        if (replacedTextRange.getEndOffset() <= editor.getDocument().getTextLength()) {
          if (restoreStickySelection) {
            EditorEx editorEx = (EditorEx)editor;
            CaretModel caretModel = editorEx.getCaretModel();
            caretModel.moveToOffset(ltrSelection ? replacedTextRange.getStartOffset() : replacedTextRange.getEndOffset());
            editorEx.setStickySelection(true);
            caretModel.moveToOffset(ltrSelection ? replacedTextRange.getEndOffset() : replacedTextRange.getStartOffset());
          }
          else {
            if (ltrSelection || editor instanceof EditorWindow) {
              editor.getSelectionModel().setSelection(replacedTextRange.getStartOffset(), replacedTextRange.getEndOffset());
            }
            else {
              editor.getSelectionModel().setSelection(replacedTextRange.getEndOffset(), replacedTextRange.getStartOffset());
            }
            if (Registry.is("editor.smarterSelectionQuoting")) {
              editor.getCaretModel().moveToOffset(ltrSelection ? replacedTextRange.getEndOffset() : replacedTextRange.getStartOffset());
            }
          }
        }
        return Result.STOP;
      }
    }
    return super.beforeSelectionRemoved(c, project, editor, file);
  }

  private static boolean shouldSkipReplacementOfQuotesOrBraces(PsiFile psiFile, Editor editor, String selectedText, char c) {
    for(DequotingFilter filter: Extensions.getExtensions(EP_NAME)) {
      if (filter.skipReplacementQuotesOrBraces(psiFile, editor, selectedText, c)) return true;
    }
    return false;
  }

  private static char getMatchingDelimiter(char c) {
    if (c == '(') return ')';
    if (c == '[') return ']';
    if (c == '{') return '}';
    if (c == '<') return '>';
    return c;
  }

  private static boolean isDelimiter(final char c) {
    return isBracket(c) || isQuote(c);
  }

  private static boolean isBracket(final char c) {
    return c == '(' || c == '{' || c == '[' || c == '<';
  }

  private static boolean isQuote(final char c) {
    return c == '"' || c == '\'' || c == '`';
  }

  private static boolean isSimilarDelimiters(final char c1, final char c2) {
    return (isBracket(c1) && isBracket(c2)) || (isQuote(c1) && isQuote(c2));
  }

  public static abstract class DequotingFilter {
    public abstract boolean skipReplacementQuotesOrBraces(@NotNull PsiFile file,
                                                          @NotNull Editor editor,
                                                          @NotNull String selectedText,
                                                          char c);
  }
}
