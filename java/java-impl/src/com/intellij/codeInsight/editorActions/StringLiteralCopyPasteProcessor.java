// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.editorActions;

import com.google.common.base.Strings;
import com.intellij.application.options.CodeStyle;
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
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.impl.source.resolve.reference.impl.manipulators.StringLiteralManipulator;
import com.intellij.psi.util.PsiLiteralUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.DataFlavor;
import java.util.Collections;

import static com.intellij.openapi.util.text.StringUtil.unescapeStringCharacters;

public class StringLiteralCopyPasteProcessor implements CopyPastePreProcessor {

  @Override
  public String preprocessOnCopy(final PsiFile file, final int[] startOffsets, final int[] endOffsets, final String text) {
    if (!isSupportedFile(file)) {
      return null;
    }

    // The main idea is to un-escape string/char literals content if necessary.
    // Example:
    //    Suppose we have a following text at the editor: String s = "first <selection>line \n second</selection> line"
    //    When user copies the selection we want to transform text \n to the real line feed, i.e. clipboard should contains the following:
    //        line 
    //        second
    //
    // However, we don't want to un-escape literal content if it's copied completely.
    // Example:
    //     String s = <selection>"my string"</selection>;

    StringBuilder buffer = new StringBuilder();
    int givenTextOffset = 0;
    boolean textWasChanged = false;
    int deducedBlockSelectionWidth = deduceBlockSelectionWidth(startOffsets, endOffsets, text);
    for (int i = 0; i < startOffsets.length && givenTextOffset < text.length(); i++, givenTextOffset++) {
      if (i > 0) {
        buffer.append('\n'); // LF is added for block selection
      }
      // Calculate offsets offsets of the selection interval being processed now.
      final int fileStartOffset = startOffsets[i];
      final int fileEndOffset = endOffsets[i];
      int givenTextStartOffset = givenTextOffset;
      final int givenTextEndOffset = givenTextOffset + (fileEndOffset - fileStartOffset);
      givenTextOffset = givenTextEndOffset;
      if (givenTextOffset > text.length()) {
        // This can happen e.g. line terminators were normalized in copied text, and it became shorter
        // than corresponding fragment in the original document.
        // We don't implement escaping/unescaping logic for documents with non-normalized line terminators currently.
        return null;
      }
      String fragment = text.substring(givenTextStartOffset, givenTextEndOffset);
      PsiElement element = file.findElementAt(fileStartOffset);
      TextRange escapedRange = element == null ? null : getEscapedRange(element);
      if (escapedRange == null || escapedRange.getStartOffset() > fileStartOffset || escapedRange.getEndOffset() < fileEndOffset) {
        buffer.append(fragment);
      }
      else {
        String unescaped = unescape(fragment, element);
        if (unescaped != null) {
          textWasChanged = true;
          buffer.append(unescaped);
        }
        else {
          buffer.append(fragment);
        }
      }
      int blockSelectionPadding = deducedBlockSelectionWidth - (fileEndOffset - fileStartOffset);
      for (int j = 0; j < blockSelectionPadding; j++) {
        buffer.append(' ');
        givenTextOffset++;
      }
    }
    return textWasChanged ? buffer.toString() : null;
  }

  private static int deduceBlockSelectionWidth(int[] startOffsets, int[] endOffsets, final String text) {
    int fragmentCount = startOffsets.length;
    assert fragmentCount > 0;
    int totalLength = fragmentCount - 1; // number of line breaks inserted between fragments
    for (int i = 0; i < fragmentCount; i++) {
      totalLength += endOffsets[i] - startOffsets[i];
    }
    if (totalLength < text.length() && (text.length() + 1) % fragmentCount == 0) {
      return (text.length() + 1) / fragmentCount - 1;
    }
    else {
      return -1;
    }
  }

  @Nullable
  protected String unescape(String text, PsiElement token) {
    return unescapeStringCharacters(text);
  }

  @NotNull
  @Override
  public String preprocessOnPaste(final Project project, final PsiFile file, final Editor editor, String text, final RawText rawText) {
    if (!isSupportedFile(file)) {
      return text;
    }

    final Document document = editor.getDocument();
    PsiDocumentManager.getInstance(project).commitDocument(document);
    final SelectionModel selectionModel = editor.getSelectionModel();

    // pastes in block selection mode (column mode) are not handled by a CopyPasteProcessor
    final int selectionStart = selectionModel.getSelectionStart();
    final int selectionEnd = selectionModel.getSelectionEnd();
    PsiElement token = findLiteralTokenType(file, selectionStart, selectionEnd);
    if (token == null) {
      return text;
    }

    if (rawText != null && wasUnescaped(text, rawText.rawText) && isSuitableForContext(rawText.rawText, token)) return rawText.rawText;
    
    if (isStringLiteral(token)) {
      text = escapeAndSplit(text, token);
    }
    else if (isCharLiteral(token)) {
      return escapeCharCharacters(text, token);
    }
    else if (isTextBlock(token)) {
      final String before = document.getText(new TextRange(selectionStart - 1, selectionStart));
      final String after = document.getText(new TextRange(selectionEnd, selectionEnd + 1));
      int caretOffset = editor.getCaretModel().getOffset();
      int offset = caretOffset - document.getLineStartOffset(document.getLineNumber(caretOffset));
      return escapeTextBlock(text, offset, "\"".equals(before), "\"".equals(after));
    }
    return text;
  }

  protected boolean isSupportedFile(PsiFile file) {
    return file instanceof PsiJavaFile;
  }

  private boolean isSuitableForContext(String text, PsiElement context) {
    if (isStringLiteral(context)) {
      return !containsUnescapedChars(text, '"', '\n');
    }
    else if (isCharLiteral(context)) {
      return !containsUnescapedChars(text, '\'', '\n');
    }
    else if (isTextBlock(context)) {
      return !text.contains("\"\"\"");
    }
    else {
      return true;
    }
  }

  private static boolean containsUnescapedChars(String text, char... cs) {
    boolean slash = false;
    for (int i = 0; i < text.length(); i++) {
      char ch = text.charAt(i);
      if (ch == '\\') {
        slash = !slash;
      }
      else {
        if (!slash) {
          for (char c : cs) {
            if (ch == c) return true;
          }
        }
        slash = false;
      }
    }
    return false;
  }

  public String escapeAndSplit(String text, PsiElement token) {
    StringBuilder buffer = new StringBuilder(text.length());
    @NonNls String breaker = getLineBreaker(token);
    final String[] lines = LineTokenizer.tokenize(text.toCharArray(), false, true);
    for (int i = 0; i < lines.length; i++) {
      buffer.append(escapeCharCharacters(lines[i], token));
      if (i != lines.length - 1) {
        buffer.append(breaker);
      }
      else if (text.endsWith("\n")) {
        buffer.append("\\n");
      }
    }
    text = buffer.toString();
    return text;
  }

  private static boolean wasUnescaped(String text, String originalText) {
    try {
      return new TextBlockTransferable(unescapeStringCharacters(originalText), Collections.emptyList(), null)
        .getTransferData(DataFlavor.stringFlavor).equals(text);
    }
    catch (Exception e) {
      throw new RuntimeException(e); // shouldn't happen
    }
  }

  protected String getLineBreaker(@NotNull PsiElement token) {
    CommonCodeStyleSettings codeStyleSettings = CodeStyle.getLanguageSettings(token.getContainingFile());
    return codeStyleSettings.BINARY_OPERATION_SIGN_ON_NEXT_LINE ? "\\n\"\n+ \"" : "\\n\" +\n\"";
  }

  @Nullable
  protected PsiElement findLiteralTokenType(PsiFile file, int selectionStart, int selectionEnd) {
    final PsiElement elementAtSelectionStart = file.findElementAt(selectionStart);
    final PsiElement elementAtSelectionEnd = file.findElementAt(selectionEnd);
    if (elementAtSelectionStart == null || elementAtSelectionEnd == null ||
        elementAtSelectionEnd.getNode().getElementType() != elementAtSelectionStart.getNode().getElementType()) {
      return null;
    }
    if (!isStringLiteral(elementAtSelectionStart) && !isCharLiteral(elementAtSelectionStart) && !isTextBlock(elementAtSelectionStart)) {
      return null;
    }
    final TextRange startTextRange = getEscapedRange(elementAtSelectionStart);
    final TextRange endTextRange = getEscapedRange(elementAtSelectionEnd);
    if (startTextRange == null || endTextRange == null ||
        !startTextRange.containsOffset(selectionStart) || !endTextRange.containsOffset(selectionEnd)) {
      return null;
    }
    return elementAtSelectionStart;
  }

  protected boolean isCharLiteral(@NotNull PsiElement token) {
    ASTNode node = token.getNode();
    return node != null && node.getElementType() == JavaTokenType.CHARACTER_LITERAL;
  }

  protected boolean isStringLiteral(@NotNull PsiElement token) {
    ASTNode node = token.getNode();
    return node != null && node.getElementType() == JavaTokenType.STRING_LITERAL;
  }
  
  protected boolean isTextBlock(@NotNull PsiElement token) {
    ASTNode node = token.getNode();
    return node != null && node.getElementType() == JavaTokenType.TEXT_BLOCK_LITERAL;
  }

  @Nullable
  protected TextRange getEscapedRange(@NotNull PsiElement token) {
    PsiElement parent = token.getParent();
    if (!(parent instanceof PsiLiteralExpression)) return null;
    final TextRange valueTextRange = StringLiteralManipulator.getValueRange((PsiLiteralExpression)token.getParent());
    return valueTextRange.shiftRight(token.getTextRange().getStartOffset());
  }

  @NotNull
  protected String escapeCharCharacters(@NotNull String s, @NotNull PsiElement token) {
    StringBuilder buffer = new StringBuilder();
    StringUtil.escapeStringCharacters(s.length(), s, isStringLiteral(token) ? "\"" : "'", buffer);
    return buffer.toString();
  }

  @NotNull
  protected String escapeTextBlock(@NotNull String text, int offset, boolean escapeStartQuote, boolean escapeEndQuote) {
    StringBuilder buffer = new StringBuilder(text.length());
    final String[] lines = LineTokenizer.tokenize(text.toCharArray(), false, false);
    String indent = Strings.repeat(" ", offset);
    for (int i = 0; i < lines.length; i++) {
      String content = PsiLiteralUtil.escapeBackSlashesInTextBlock(lines[i]);
      content = PsiLiteralUtil.escapeTextBlockCharacters(content, i == 0 && escapeStartQuote,
                                                         i == lines.length - 1 && escapeEndQuote, true);
      buffer.append(content);
      if (i < lines.length - 1) {
        buffer.append('\n').append(indent);
      }
    }
    return buffer.toString();
  }
}
