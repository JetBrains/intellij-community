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
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.CaretAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
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
  private boolean myRestoreStickySelection;

  private static final Key<String> REPLACEMENT_TEXT = Key.create("SelectionQuotingTypedHandler.replacementText");
  private static final Key<TextRange> REPLACED_TEXT_RANGE = Key.create("SelectionQuotingTypedHandler.replacedTextRange");
  private static final Key<Boolean> LTR_SELECTION = Key.create("SelectionQuotingTypedHandler.ltrSelection");

  @Override
  public Result checkAutoPopup(final char c, Project project, final Editor editor, final PsiFile psiFile) {
    // TODO[oleg] provide adequate API not to use this hack
    // beforeCharTyped always works with removed selection
    if(CodeInsightSettings.getInstance().SURROUND_SELECTION_ON_QUOTE_TYPED &&  editor.getSelectionModel().hasSelection(true) && isDelimiter(c)) {
      myRestoreStickySelection = (editor instanceof EditorEx) && ((EditorEx)editor).isStickySelection();

      // first we determine replacement texts for each caret, then we apply all the changes
      // this is to make PSI consistent with document state for the calculation of replacement text for all carets
      editor.getCaretModel().runForEachCaret(new CaretAction() {
        @Override
        public void perform(Caret caret) {
          String replacementText = null;
          String selectedText = caret.getSelectedText();
          if (!StringUtil.isEmpty(selectedText)) {
            if (selectedText.length() > 1) {
              final char firstChar = selectedText.charAt(0);
              final char lastChar = selectedText.charAt(selectedText.length() - 1);
              if (isSimilarDelimiters(firstChar, c) && lastChar == getMatchingDelimiter(firstChar) &&
                  (isQuote(firstChar) || firstChar != c) && !shouldSkipReplacementOfQuotesOrBraces(psiFile, editor, selectedText, c) &&
                  selectedText.indexOf(lastChar, 1) == selectedText.length() - 1) {
                selectedText = selectedText.substring(1, selectedText.length() - 1);
              }
            }
            final char c2 = getMatchingDelimiter(c);
            replacementText = String.valueOf(c) + selectedText + c2;
          }
          caret.putUserData(REPLACEMENT_TEXT, replacementText);
        }
      });
      editor.getCaretModel().runForEachCaret(new CaretAction() {
        @Override
        public void perform(Caret caret) {
          String replacementText = caret.getUserData(REPLACEMENT_TEXT);
          caret.putUserData(REPLACEMENT_TEXT, null);
          if (replacementText == null) {
            caret.putUserData(LTR_SELECTION, null);
            caret.putUserData(REPLACED_TEXT_RANGE, null);
          }
          else {
            int selectionStart = caret.getSelectionStart();
            int selectionEnd = caret.getSelectionEnd();
            caret.putUserData(LTR_SELECTION, caret.getLeadSelectionOffset() != selectionEnd);
            caret.putUserData(REPLACED_TEXT_RANGE, Registry.is("editor.smarterSelectionQuoting")
                                                   ? new TextRange(selectionStart + 1, selectionStart + replacementText.length() - 1)
                                                   : new TextRange(selectionStart, selectionStart + replacementText.length()));
            caret.removeSelection();
            editor.getDocument().replaceString(selectionStart, selectionEnd, replacementText);
          }
        }
      });
      return Result.STOP;
    }
    return super.checkAutoPopup(c, project, editor, psiFile);
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

  @Override
  public Result beforeCharTyped(final char charTyped, final Project project, final Editor editor, final PsiFile file, final FileType fileType) {
    // TODO[oleg] remove this hack when API changes
    final Ref<Boolean> caretUpdated = new Ref<Boolean>(Boolean.FALSE);
    editor.getCaretModel().runForEachCaret(new CaretAction() {
      @Override
      public void perform(Caret caret) {
        TextRange replacedTextRange = caret.getUserData(REPLACED_TEXT_RANGE);
        caret.putUserData(REPLACED_TEXT_RANGE, null);
        if (replacedTextRange != null) {
          Boolean ltrSelectionBoxed = caret.getUserData(LTR_SELECTION);
          caret.putUserData(LTR_SELECTION, null);
          boolean ltrSelection = ltrSelectionBoxed == null ? false : ltrSelectionBoxed;
          if (replacedTextRange.getEndOffset() <= editor.getDocument().getTextLength()) {
            if (myRestoreStickySelection && editor instanceof EditorEx) {
              EditorEx editorEx = (EditorEx)editor;
              editorEx.setStickySelection(false);
              caret.moveToOffset(ltrSelection ? replacedTextRange.getStartOffset() : replacedTextRange.getEndOffset());
              editorEx.setStickySelection(true);
              caret.moveToOffset(ltrSelection ? replacedTextRange.getEndOffset() : replacedTextRange.getStartOffset());
            }
            else {
              if (ltrSelection || editor instanceof EditorWindow) {
                caret.setSelection(replacedTextRange.getStartOffset(), replacedTextRange.getEndOffset());
              }
              else {
                caret.setSelection(replacedTextRange.getEndOffset(), replacedTextRange.getStartOffset());
              }
              if (Registry.is("editor.smarterSelectionQuoting")) {
                caret.moveToOffset(ltrSelection ? replacedTextRange.getEndOffset() : replacedTextRange.getStartOffset());
              }
            }
          }
          caretUpdated.set(Boolean.TRUE);
        }
      }
    });
    return caretUpdated.get() ? Result.STOP : Result.CONTINUE;
  }
  
  public static abstract class DequotingFilter {
    public abstract boolean skipReplacementQuotesOrBraces(@NotNull PsiFile file,
                                                          @NotNull Editor editor,
                                                          @NotNull String selectedText,
                                                          char c);
  }
}
