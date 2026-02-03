// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions.fillParagraph;

import com.intellij.application.options.CodeStyle;
import com.intellij.formatting.FormatterTagHandler;
import com.intellij.formatting.LineWrappingUtil;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.UnfairTextRange;
import com.intellij.openapi.util.text.CharFilter;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Defines general re-flow paragraph functionality.
 * Serves plain text files.
 *
 * User : ktisha
 */
public class ParagraphFillHandler {

  protected void performOnElement(final @NotNull PsiElement element, final @NotNull Editor editor) {
    final Document document = editor.getDocument();

    final TextRange textRange = getTextRange(element, editor);
    if (textRange.isEmpty()) return;
    final String text = textRange.substring(element.getContainingFile().getText());

    final List<String> subStrings = StringUtil.split(text, "\n", true);
    final String prefix = getPrefix(element);
    final String postfix = getPostfix(element);

    final StringBuilder stringBuilder = new StringBuilder();
    appendPrefix(element, text, stringBuilder);

    stringBuilder.append(subStrings.stream()
                           .map(string -> StringUtil.trimStart(string.trim(), prefix.trim()))
                           .map(string -> StringUtil.trimEnd(string, postfix.trim()))
                           .map(String::trim)
                           .filter(finalString -> !StringUtil.isEmptyOrSpaces(finalString))
                           .collect(Collectors.joining(" ")));

    appendPostfix(element, text, stringBuilder);

    final String replacementText = stringBuilder.toString();

    CommandProcessor.getInstance().executeCommand(element.getProject(), () -> {
      document.replaceString(textRange.getStartOffset(), textRange.getEndOffset(),
                             replacementText);
      final PsiFile file = element.getContainingFile();
      FormatterTagHandler formatterTagHandler = new FormatterTagHandler(CodeStyle.getSettings(file));
      List<TextRange> enabledRanges = formatterTagHandler.getEnabledRanges(file.getNode(), TextRange.create(0, document.getTextLength()));

      LineWrappingUtil.doWrapLongLinesIfNecessary(editor, element.getProject(), document,
                                                  textRange.getStartOffset(),
                                                            textRange.getStartOffset() + replacementText.length() + 1,
                                                  enabledRanges,
                                                  CodeStyle.getSettings(file).getRightMargin(element.getLanguage()));
    }, null, document);

  }

  protected void appendPostfix(final @NotNull PsiElement element,
                               final @NotNull String text,
                               final @NotNull StringBuilder stringBuilder) {
    final String postfix = getPostfix(element);
    if (text.endsWith(postfix.trim()))
      stringBuilder.append(postfix);
  }

  protected void appendPrefix(final @NotNull PsiElement element,
                              final @NotNull String text,
                              final @NotNull StringBuilder stringBuilder) {
    final String prefix = getPrefix(element);
    if (text.startsWith(prefix.trim()))
      stringBuilder.append(prefix);
  }

  private TextRange getTextRange(final @NotNull PsiElement element, final @NotNull Editor editor) {
    int startOffset = getStartOffset(element, editor);
    int endOffset = getEndOffset(element, editor);
    return new UnfairTextRange(startOffset, endOffset);
  }

  private int getStartOffset(final @NotNull PsiElement element, final @NotNull Editor editor) {
    if (isBunchOfElement(element)) {
      final PsiElement firstElement = getFirstElement(element);
      return firstElement.getTextRange().getStartOffset();
    }
    final int offset = editor.getCaretModel().getOffset();
    final int elementTextOffset = element.getTextOffset();
    final Document document = editor.getDocument();
    int lineNumber = document.getLineNumber(offset);

    while (lineNumber != document.getLineNumber(elementTextOffset)) {
      final String text = document.getText(TextRange.create(document.getLineStartOffset(lineNumber),
                                                            document.getLineEndOffset(lineNumber)));
      if (StringUtil.isEmptyOrSpaces(text)) {
        lineNumber += 1;
        break;
      }
      lineNumber -= 1;
    }
    final int lineStartOffset = lineNumber == document.getLineNumber(elementTextOffset) ? elementTextOffset : document.getLineStartOffset(lineNumber);
    final String lineText = document
      .getText(TextRange.create(lineStartOffset, document.getLineEndOffset(lineNumber)));
    int shift = StringUtil.findFirst(lineText, CharFilter.NOT_WHITESPACE_FILTER);

    return lineStartOffset + shift;
  }

  protected boolean isBunchOfElement(PsiElement element) {
    return element instanceof PsiComment;
  }

  private int getEndOffset(final @NotNull PsiElement element, final @NotNull Editor editor) {
    if (isBunchOfElement(element)) {
      final PsiElement next = getLastElement(element);
      return next.getTextRange().getEndOffset();
    }
    final int offset = editor.getCaretModel().getOffset();
    final int elementTextOffset = element.getTextRange().getEndOffset();
    final Document document = editor.getDocument();
    int lineNumber = document.getLineNumber(offset);

    while (lineNumber != document.getLineNumber(elementTextOffset)) {
      final String text = document.getText(TextRange.create(document.getLineStartOffset(lineNumber),
                                                            document.getLineEndOffset(lineNumber)));
      if (StringUtil.isEmptyOrSpaces(text)) {
        lineNumber -= 1;
        break;
      }
      lineNumber += 1;
    }
    return document.getLineEndOffset(lineNumber);
  }

  private @NotNull PsiElement getFirstElement(final @NotNull PsiElement element) {
    final IElementType elementType = element.getNode().getElementType();
    PsiElement prevSibling = element.getPrevSibling();
    PsiElement result = element;
    while (prevSibling != null && (prevSibling.getNode().getElementType().equals(elementType) ||
                                   (atWhitespaceToken(prevSibling) &&
                                    StringUtil.countChars(prevSibling.getText(), '\n') <= 1))) {
      String text = prevSibling.getText();
      final String prefix = getPrefix(element);
      final String postfix = getPostfix(element);
      text = StringUtil.trimStart(text.trim(), prefix.trim());
      text = StringUtil.trimEnd(text, postfix);

      if (prevSibling.getNode().getElementType().equals(elementType) &&
          StringUtil.isEmptyOrSpaces(text)) {
        break;
      }
      if (prevSibling.getNode().getElementType().equals(elementType)) {
        result = prevSibling;
      }
      prevSibling = prevSibling.getPrevSibling();
    }
    return result;
  }

  private @NotNull PsiElement getLastElement(final @NotNull PsiElement element) {
    final IElementType elementType = element.getNode().getElementType();
    PsiElement nextSibling = element.getNextSibling();
    PsiElement result = element;
    while (nextSibling != null && (nextSibling.getNode().getElementType().equals(elementType) ||
                                   (atWhitespaceToken(nextSibling) &&
                                    StringUtil.countChars(nextSibling.getText(), '\n') <= 1))) {
      String text = nextSibling.getText();
      final String prefix = getPrefix(element);
      final String postfix = getPostfix(element);
      text = StringUtil.trimStart(text.trim(), prefix.trim());
      text = StringUtil.trimEnd(text, postfix);

      if (nextSibling.getNode().getElementType().equals(elementType) &&
          StringUtil.isEmptyOrSpaces(text)) {
        break;
      }
      if (nextSibling.getNode().getElementType().equals(elementType)) {
        result = nextSibling;
      }
      nextSibling = nextSibling.getNextSibling();
    }
    return result;
  }

  protected boolean atWhitespaceToken(final @Nullable PsiElement element) {
    return element instanceof PsiWhiteSpace;
  }

  protected boolean isAvailableForElement(final @Nullable PsiElement element) {
    return element != null;
  }

  protected boolean isAvailableForFile(final @Nullable PsiFile psiFile) {
    return psiFile instanceof PsiPlainTextFile;
  }

  protected @NotNull String getPrefix(final @NotNull PsiElement element) {
    return "";
  }

  protected @NotNull String getPostfix(final @NotNull PsiElement element) {
    return "";
  }

}
