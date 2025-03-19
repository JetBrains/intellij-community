// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.ParserDefinition;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SelectionQuotingTypedHandler extends TypedHandlerDelegate {
  private static final ExtensionPointName<UnquotingFilter> EP_NAME = ExtensionPointName.create("com.intellij.selectionUnquotingFilter");

  @Override
  public @NotNull Result beforeSelectionRemoved(char c, @NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    SelectionModel selectionModel = editor.getSelectionModel();
    if (CodeInsightSettings.getInstance().AUTOINSERT_PAIR_QUOTE && selectionModel.hasSelection() && isQuote(c)) {
      String selectedText = selectionModel.getSelectedText();
      if (selectedText != null && selectedText.length() == 1) {
        char selectedChar = selectedText.charAt(0);
        if (isQuote(selectedChar) &&
            selectedChar != c &&
            !shouldSkipReplacementOfQuotesOrBraces(file, editor, selectedText, c) &&
            replaceQuotesBySelected(c, editor, file, selectionModel, selectedChar)) {
          return Result.STOP;
        }
      }
    }
    if (CodeInsightSettings.getInstance().SURROUND_SELECTION_ON_QUOTE_TYPED && selectionModel.hasSelection() && isDelimiter(c)) {
      String selectedText = selectionModel.getSelectedText();
      if (!StringUtil.isEmpty(selectedText)) {
        final int selectionStart = selectionModel.getSelectionStart();
        final int selectionEnd = selectionModel.getSelectionEnd();

        if (isReplaceInComparisonOperation(file, selectionStart, selectedText, c)) {
          return super.beforeSelectionRemoved(c, project, editor, file);
        }

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
        final String newText = c + selectedText + c2;
        boolean ltrSelection = selectionModel.getLeadSelectionOffset() != selectionModel.getSelectionEnd();
        boolean restoreStickySelection = editor instanceof EditorEx && ((EditorEx)editor).isStickySelection();
        selectionModel.removeSelection();
        editor.getDocument().replaceString(selectionStart, selectionEnd, newText);

        int startOffset = caretOffset + 1;
        int endOffset = caretOffset + newText.length() - 1;
        int length = editor.getDocument().getTextLength();

        // selection is removed here
        if (endOffset <= length) {
          if (restoreStickySelection) {
            EditorEx editorEx = (EditorEx)editor;
            CaretModel caretModel = editorEx.getCaretModel();
            caretModel.moveToOffset(ltrSelection ? startOffset : endOffset);
            editorEx.setStickySelection(true);
            caretModel.moveToOffset(ltrSelection ? endOffset : startOffset);
          }
          else {
            if (ltrSelection || editor instanceof EditorWindow) {
              editor.getSelectionModel().setSelection(startOffset, endOffset);
            }
            else {
              editor.getSelectionModel().setSelection(endOffset, startOffset);
            }
            editor.getCaretModel().moveToOffset(ltrSelection ? endOffset : startOffset);
          }
          if (c == '{') {
            int startOffsetToReformat = startOffset - 1;
            int endOffsetToReformat = length > endOffset ? endOffset + 1 : endOffset;
            CodeStyleManager.getInstance(project).reformatText(file, startOffsetToReformat, endOffsetToReformat);
          }
        }
        return Result.STOP;
      }
    }
    return super.beforeSelectionRemoved(c, project, editor, file);
  }

  private static boolean isReplaceInComparisonOperation(@NotNull PsiFile file, int offset, @NotNull String selectedText, char c) {
    if ((c == '<' || c == '>') && selectedText.length() <= 3 && isOnlyComparisons(selectedText)) {
      PsiElement elementAtOffset = file.findElementAt(offset);
      if (elementAtOffset != null) {
        IElementType tokenType = elementAtOffset.getNode().getElementType();
        ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(elementAtOffset.getLanguage());
        if (parserDefinition != null &&
            (parserDefinition.getCommentTokens().contains(tokenType) ||
             parserDefinition.getStringLiteralElements().contains(tokenType))) {
          return false;
        }
      }
      return true;
    }
    return false;
  }

  private static boolean isOnlyComparisons(@NotNull String text) {
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c != '>' && c != '<' && c != '=' && c != '!') {
        return false;
      }
    }
    return true;
  }

  /**
   * @return list of pairs (offset, is escaped)
   */
  private static @NotNull List<Pair<Integer, Boolean>> containsCharInside(String selectedText, char selected, char typed) {
    boolean isEscaped = false;
    List<Pair<Integer, Boolean>> result = null;
    for (int i = 0; i < selectedText.length(); i++) {
      char c = selectedText.charAt(i);
      if (isEscaped) {
        if (c == selected || c == typed) {
          if (result == null) result = new ArrayList<>();
          result.add(Pair.create(i, true));
        }
        isEscaped = false;
      }
      else if (c == '\\') {
        isEscaped = true;
      }
      else if (c == selected || c == typed) {
        if (result == null) result = new ArrayList<>();
        result.add(Pair.create(i, false));
      }
    }
    return result != null ? result : Collections.emptyList();
  }

  private static boolean replaceQuotesBySelected(char typed,
                                                 @NotNull Editor editor,
                                                 @NotNull PsiFile file,
                                                 @NotNull SelectionModel selectionModel,
                                                 char selectedChar) {
    int selectionStart = selectionModel.getSelectionStart();
    PsiElement leaf = file.findElementAt(selectionStart);
    PsiElement element = leaf;
    while (element != null) {
      TextRange textRange = element.getTextRange();
      if (textRange != null && textRange.getLength() >= 2 &&
          (selectionStart == textRange.getStartOffset() || textRange.getEndOffset() == selectionStart + 1)) {
        int matchingCharOffset = selectionStart == textRange.getStartOffset() ? textRange.getEndOffset() - 1 : textRange.getStartOffset();
        Document document = editor.getDocument();
        CharSequence charsSequence = document.getCharsSequence();
        if (matchingCharOffset < charsSequence.length()) {
          char matchingChar = charsSequence.charAt(matchingCharOffset);
          boolean otherQuoteMatchesSelected = matchingChar == selectedChar;
          if (otherQuoteMatchesSelected) {
            boolean handleEscaping = element == leaf; // can't handle interpolation
            TextRange innerRange = TextRange.create(textRange.getStartOffset() + 1, textRange.getEndOffset() - 1);
            String body = document.getText(innerRange);
            List<Pair<Integer, Boolean>> quotesInside = containsCharInside(body, selectedChar, typed);
            if (handleEscaping || quotesInside.isEmpty()) {
              replaceChar(document, textRange.getStartOffset(), typed);
              replaceChar(document, textRange.getEndOffset() - 1, typed);
              int offsetDelta = 0;
              if (handleEscaping && !quotesInside.isEmpty()) {
                StringBuilder newBody = new StringBuilder(body);
                for (Pair<Integer, Boolean> pair : quotesInside) {
                  int offsetInBody = pair.first;
                  char quote = body.charAt(offsetInBody);
                  if (quote == typed && !pair.second) {
                    newBody.insert(offsetInBody + offsetDelta, '\\');
                    offsetDelta++;
                  }
                  else if (quote == selectedChar && pair.second) {
                    newBody.deleteCharAt(offsetInBody + offsetDelta - 1);
                    offsetDelta--;
                  }
                }
                document.replaceString(innerRange.getStartOffset(), innerRange.getEndOffset(), newBody);
              }
              editor.getCaretModel().moveToOffset(selectionModel.getSelectionEnd());
              selectionModel.removeSelection();
              return true;
            }
          }
        }
      }
      if (element instanceof PsiFile) break;
      element = element.getParent();
    }
    return false;
  }

  public static boolean shouldSkipReplacementOfQuotesOrBraces(PsiFile psiFile, Editor editor, String selectedText, char c) {
    return EP_NAME.getExtensionList().stream().anyMatch(filter -> filter.skipReplacementQuotesOrBraces(psiFile, editor, selectedText, c));
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

  private static void replaceChar(Document document, int offset, char newChar) {
    document.replaceString(offset, offset + 1, String.valueOf(newChar));
  }

  public abstract static class UnquotingFilter {
    public abstract boolean skipReplacementQuotesOrBraces(@NotNull PsiFile file,
                                                          @NotNull Editor editor,
                                                          @NotNull String selectedText,
                                                          char c);
  }
}
