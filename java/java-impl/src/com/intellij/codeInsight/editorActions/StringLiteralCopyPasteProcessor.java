/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.DataFlavor;
import java.util.Collections;

import static com.intellij.openapi.util.text.StringUtil.unescapeStringCharacters;

public class StringLiteralCopyPasteProcessor implements CopyPastePreProcessor {

  @SuppressWarnings("ForLoopThatDoesntUseLoopVariable")
  @Override
  public String preprocessOnCopy(final PsiFile file, final int[] startOffsets, final int[] endOffsets, final String text) {
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
        textWasChanged = true;
        buffer.append(unescape(fragment, element));
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

  @NotNull
  protected String unescape(String text, PsiElement token) {
    return unescapeStringCharacters(text);
  }

  @NotNull
  @Override
  public String preprocessOnPaste(final Project project, final PsiFile file, final Editor editor, String text, final RawText rawText) {
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

    if (rawText != null && wasUnescaped(text, rawText.rawText)) return rawText.rawText;
    
    if (isStringLiteral(token)) {
      text = escapeAndSplit(text, token);
    }
    else if (isCharLiteral(token)) {
      return escapeCharCharacters(text, token);
    }
    return text;
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
    if (elementAtSelectionStart == null) {
      return null;
    }
    if (!isStringLiteral(elementAtSelectionStart) && !isCharLiteral(elementAtSelectionStart)) {
      return null;
    }

    if (elementAtSelectionStart.getTextRange().getEndOffset() < selectionEnd) {
      final PsiElement elementAtSelectionEnd = file.findElementAt(selectionEnd);
      if (elementAtSelectionEnd == null) {
        return null;
      }
      if (elementAtSelectionEnd.getNode().getElementType() == elementAtSelectionStart.getNode().getElementType() &&
          elementAtSelectionEnd.getTextRange().getStartOffset() < selectionEnd) {
        return elementAtSelectionStart;
      }
    }
    
    final TextRange textRange = elementAtSelectionStart.getTextRange();
    if (selectionStart <= textRange.getStartOffset() || selectionEnd >= textRange.getEndOffset()) {
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

  @Nullable
  protected TextRange getEscapedRange(@NotNull PsiElement token) {
    if (isCharLiteral(token) || isStringLiteral(token)) {
      TextRange tokenRange = token.getTextRange();
      return new TextRange(tokenRange.getStartOffset() + 1, tokenRange.getEndOffset() - 1); // Excluding String/char literal quotes
    }
    else {
      return null;
    }
  }

  @NotNull
  protected String escapeCharCharacters(@NotNull String s, @NotNull PsiElement token) {
    StringBuilder buffer = new StringBuilder();
    StringUtil.escapeStringCharacters(s.length(), s, isStringLiteral(token) ? "\"" : "\'", buffer);
    return buffer.toString();
  }
}
