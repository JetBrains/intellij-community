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
import com.intellij.psi.util.PsiTreeUtil;
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
      int givenTextStartOffset = Math.min(givenTextOffset, text.length());
      final int givenTextEndOffset = Math.min(givenTextOffset + (fileEndOffset - fileStartOffset), text.length());
      givenTextOffset = givenTextEndOffset;
      for (
        PsiElement element = file.findElementAt(fileStartOffset);
        givenTextStartOffset < givenTextEndOffset;
        element = PsiTreeUtil.nextLeaf(element)) {
        if (element == null) {
          buffer.append(text.substring(givenTextStartOffset, givenTextEndOffset));
          break;
        }
        TextRange elementRange = element.getTextRange();
        int escapedStartOffset;
        int escapedEndOffset;
        if ((isStringLiteral(element) || isCharLiteral(element))
            // We don't want to un-escape if complete literal is copied.
            && (elementRange.getStartOffset() < fileStartOffset || elementRange.getEndOffset() > fileEndOffset)) {
          escapedStartOffset = elementRange.getStartOffset() + 1 /* String/char literal quote */;
          escapedEndOffset = elementRange.getEndOffset() - 1 /* String/char literal quote */;
        }
        else {
          escapedStartOffset = escapedEndOffset = elementRange.getStartOffset();
        }

        // Process text to the left of the escaped fragment (if any).
        int numberOfSymbolsToCopy = escapedStartOffset - Math.max(fileStartOffset, elementRange.getStartOffset());
        if (numberOfSymbolsToCopy > 0) {
          buffer.append(text.substring(givenTextStartOffset, givenTextStartOffset + numberOfSymbolsToCopy));
          givenTextStartOffset += numberOfSymbolsToCopy;
        }

        // Process escaped text (un-escape it).
        numberOfSymbolsToCopy = Math.min(escapedEndOffset, fileEndOffset) - Math.max(fileStartOffset, escapedStartOffset);
        if (numberOfSymbolsToCopy > 0) {
          textWasChanged = true;
          buffer.append(unescape(text.substring(givenTextStartOffset, givenTextStartOffset + numberOfSymbolsToCopy), element));
          givenTextStartOffset += numberOfSymbolsToCopy;
        }

        // Process text to the right of the escaped fragment (if any).
        numberOfSymbolsToCopy = Math.min(fileEndOffset, elementRange.getEndOffset()) - Math.max(fileStartOffset, escapedEndOffset);
        if (numberOfSymbolsToCopy > 0) {
          buffer.append(text.substring(givenTextStartOffset, givenTextStartOffset + numberOfSymbolsToCopy));
          givenTextStartOffset += numberOfSymbolsToCopy;
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
    }
    else if (isCharLiteral(token)) {
      return escapeCharCharacters(text, token);
    }
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

  protected String getLineBreaker(PsiElement token) {
    CommonCodeStyleSettings codeStyleSettings = CodeStyleSettingsManager.getSettings(token.getProject()).getCommonSettings(token.getLanguage());
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

  @NotNull
  protected String escapeCharCharacters(@NotNull String s, @NotNull PsiElement token) {
    StringBuilder buffer = new StringBuilder();
    StringUtil.escapeStringCharacters(s.length(), s, isStringLiteral(token) ? "\"" : "\'", buffer);
    return buffer.toString();
  }
}
