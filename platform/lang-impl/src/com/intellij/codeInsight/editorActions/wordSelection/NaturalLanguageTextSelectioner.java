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

package com.intellij.codeInsight.editorActions.wordSelection;

import com.intellij.codeInsight.editorActions.ExtendWordSelectionHandlerBase;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPlainText;
import com.intellij.util.containers.CollectionFactory;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class NaturalLanguageTextSelectioner extends ExtendWordSelectionHandlerBase {
  private static final Set<Character> NATURAL = CollectionFactory.newTroveSet('(', ')', '.', ',', ':', ';', '!', '?', '$', '@', '%', '\"', '\'');
  private static final Set<Character> SENTENCE_END = CollectionFactory.newTroveSet('.', '!', '?');

  public boolean canSelect(PsiElement e) {
    return e instanceof PsiPlainText || e instanceof PsiComment;
  }

  public List<TextRange> select(PsiElement e, CharSequence editorText, int cursorOffset, Editor editor) {
    final SelectionModel selectionModel = editor.getSelectionModel();
    if (!selectionModel.hasSelection()) {
      return Collections.emptyList();
    }

    int sentenceStart = selectionModel.getSelectionStart();
    final int elementStart = e.getTextRange().getStartOffset();
    if (sentenceStart <= elementStart) return Collections.emptyList();

    int paragraphStart = editorText.subSequence(elementStart, sentenceStart).toString().lastIndexOf("\n\n");
    if (paragraphStart < 0) paragraphStart = elementStart;
    else paragraphStart += 2 + elementStart;
    boolean isParagraph = paragraphStart == sentenceStart;

    while (sentenceStart > paragraphStart) {
      final char c = editorText.charAt(sentenceStart - 1);
      if (!isNatural(c)) {
        return Collections.emptyList();
      }

      if (SENTENCE_END.contains(c)) {
        break;
      }
      sentenceStart--;
    }
    while (Character.isWhitespace(editorText.charAt(sentenceStart))) {
      sentenceStart++;
    }

    int sentenceEnd = selectionModel.getSelectionEnd();
    final int elementEnd = e.getTextRange().getEndOffset();
    if (sentenceEnd > elementEnd) {
      return Collections.emptyList();
    }
    int paragraphEnd = editorText.subSequence(sentenceEnd, elementEnd).toString().indexOf("\n\n");
    if (paragraphEnd < 0) paragraphEnd = elementEnd;
    else paragraphEnd += sentenceEnd;
    isParagraph &= paragraphEnd == sentenceEnd;

    if (isParagraph) {
      return Collections.emptyList(); //whole text
    }

    if (sentenceEnd > elementStart) sentenceEnd--;
    while (sentenceEnd < paragraphEnd) {
      final char c = editorText.charAt(sentenceEnd);
      if (!isNatural(c)) {
        return Collections.emptyList();
      }

      sentenceEnd++;

      if (SENTENCE_END.contains(c)) {
        break;
      }

    }

    if (sentenceStart == selectionModel.getSelectionStart() && sentenceEnd == selectionModel.getSelectionEnd()) {
      return Arrays.asList(new TextRange(paragraphStart, paragraphEnd));
    }

    return Arrays.asList(new TextRange(sentenceStart, sentenceEnd));
  }

  private static boolean isNatural(char c) {
    return Character.isWhitespace(c) || Character.isLetterOrDigit(c) || NATURAL.contains(c);
  }
}