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

import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.javadoc.PsiDocComment;

import java.util.List;

public class FieldSelectioner extends WordSelectioner {
  @Override
  public boolean canSelect(PsiElement e) {
    return e instanceof PsiField && e.getLanguage() == JavaLanguage.INSTANCE;
  }

  private static void addRangeElem(final List<TextRange> result,
                                   CharSequence editorText,
                                   final PsiElement first,
                                   final int end) {
    if (first != null) {
      result.addAll(expandToWholeLine(editorText,
                                      new TextRange(first.getTextRange().getStartOffset(), end)));
    }
  }

  @Override
  public List<TextRange> select(PsiElement e, CharSequence editorText, int cursorOffset, Editor editor) {
    List<TextRange> result = super.select(e, editorText, cursorOffset, editor);
    final PsiField field = (PsiField)e;
    final TextRange range = field.getTextRange();
    final PsiIdentifier first = field.getNameIdentifier();
    final TextRange firstRange = first.getTextRange();
    final PsiElement last = field.getInitializer();
    final int end = last == null ? firstRange.getEndOffset() : last.getTextRange().getEndOffset();

    PsiDocComment comment = field.getDocComment();
    if (comment != null) {
      TextRange commentTextRange = comment.getTextRange();
      addRangeElem(result, editorText, comment, commentTextRange.getEndOffset());
    }
    addRangeElem(result, editorText, first, end);
    //addRangeElem (result, editorText, field, textLength, field.getTypeElement(), end);
    addRangeElem(result, editorText, field.getModifierList(), range.getEndOffset());
    //addRangeElem (result, editorText, field, textLength, field.getDocComment(), end);
    result.addAll(expandToWholeLine(editorText, range));
    return result;
  }
}
