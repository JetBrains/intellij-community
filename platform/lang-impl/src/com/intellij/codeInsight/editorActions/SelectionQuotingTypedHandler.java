/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author AG
 * @author yole
 */
public class SelectionQuotingTypedHandler extends TypedHandlerDelegate {
  public static final ExtensionPointName<DequotingFilter> EP_NAME =
    ExtensionPointName.create("com.intellij.selectionDequotingFilter");
  private TextRange myReplacedTextRange;

  @Override
  public Result checkAutoPopup(char c, Project project, Editor editor, PsiFile psiFile) {
    // TODO[oleg] provide adequate API not to use this hack
    // beforeCharTyped always works with removed selection
    if(CodeInsightSettings.getInstance().SURROUND_SELECTION_ON_QUOTE_TYPED &&  editor.getSelectionModel().hasSelection() && isDelimiter(c)) {
      String selectedText = editor.getSelectionModel().getSelectedText();
      if (selectedText.length() < 1) {
        return super.checkAutoPopup(c, project, editor, psiFile);
      }
      if (selectedText.length() > 1) {
        final char firstChar = selectedText.charAt(0);
        if (isSimilarDelimiters(firstChar, c) &&
            selectedText.charAt(selectedText.length() - 1) == getMatchingDelimiter(firstChar) &&
            (isQuote(firstChar) || firstChar != c) &&
            !shouldSkipReplacementOfQuotesOrBraces(psiFile, editor, selectedText, c)
          ) {
          selectedText = selectedText.substring(1, selectedText.length() - 1);
        }
      }
      final int caretOffset = editor.getSelectionModel().getSelectionStart();
      final char c2 = getMatchingDelimiter(c);
      final String newText = String.valueOf(c) + selectedText + c2;
      EditorModificationUtil.insertStringAtCaret(editor, newText);
      if (Registry.is("editor.smarterSelectionQuoting")) {
        myReplacedTextRange = new TextRange(caretOffset + 1, caretOffset + newText.length() - 1);
      } else {
        myReplacedTextRange = new TextRange(caretOffset, caretOffset + newText.length());
      }
      return Result.STOP;
    }
    return super.checkAutoPopup(c, project, editor, psiFile);
  }

  private boolean shouldSkipReplacementOfQuotesOrBraces(PsiFile psiFile, Editor editor, String selectedText, char c) {
    for(DequotingFilter filter: Extensions.getExtensions(EP_NAME)) {
      if (filter.skipReplacementQuotesOrBraces(psiFile, editor, selectedText, c)) return true;
    }
    return false;
  }

  private static char getMatchingDelimiter(final char c) {
    char c2 = c;
    if (c == '(') c2 = ')';
    if (c == '[') c2 = ']';
    if (c == '{') c2 = '}';
    if (c == '<') c2 = '>';
    return c2;
  }

  private static boolean isDelimiter(final char c) {
    return isBracket(c) || isQuote(c);
  }

  private static boolean isBracket(final char c) {
    return c == '(' || c == '{' || c == '[' || c == '<';
  }

  private static boolean isQuote(final char c) {
    return c == '"' || c == '\'';
  }

  private static boolean isSimilarDelimiters(final char c1, final char c2) {
    return (isBracket(c1) && isBracket(c2)) || (isQuote(c1) && isQuote(c2));
  }

  public Result beforeCharTyped(final char charTyped, final Project project, final Editor editor, final PsiFile file, final FileType fileType) {
    // TODO[oleg] remove this hack when API changes
    if (myReplacedTextRange != null) {
      if (myReplacedTextRange.getEndOffset() <= editor.getDocument().getTextLength()) {
        editor.getSelectionModel().setSelection(myReplacedTextRange.getStartOffset(), myReplacedTextRange.getEndOffset());
        if (Registry.is("editor.smarterSelectionQuoting")) {
          editor.getCaretModel().moveToOffset(myReplacedTextRange.getEndOffset());
        }
      }
      myReplacedTextRange = null;
      return Result.STOP;
    }
    return Result.CONTINUE;
  }
  
  public static abstract class DequotingFilter {
    public abstract boolean skipReplacementQuotesOrBraces(@NotNull PsiFile file,
                                                          @NotNull Editor editor,
                                                          @NotNull String selectedText,
                                                          char c);
  }
}
