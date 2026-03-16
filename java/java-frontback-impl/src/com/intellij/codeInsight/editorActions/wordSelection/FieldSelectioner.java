// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions.wordSelection;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.javadoc.PsiDocComment;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class FieldSelectioner extends WordSelectioner {

  @Override
  public boolean canSelect(@NotNull PsiElement e) {
    return e instanceof PsiField && e.getLanguage() == JavaLanguage.INSTANCE;
  }

  private static void addRangeElem(final List<? super TextRange> result,
                                   CharSequence editorText,
                                   final PsiElement first,
                                   final int end) {
    if (first != null) {
      result.addAll(expandToWholeLine(editorText,
                                      new TextRange(first.getTextRange().getStartOffset(), end)));
    }
  }

  @Override
  public List<TextRange> select(@NotNull PsiElement e, @NotNull CharSequence editorText, int cursorOffset, @NotNull Editor editor) {
    List<TextRange> result = super.select(e, editorText, cursorOffset, editor);
    if (result == null) return null;
    PsiField field = (PsiField)e;
    TextRange fieldRange = field.getTextRange();
    PsiIdentifier nameId = field.getNameIdentifier();
    TextRange nameRange = nameId.getTextRange();
    PsiElement last = field.getInitializer();
    int end = last == null ? nameRange.getEndOffset() : last.getTextRange().getEndOffset();

    PsiDocComment comment = field.getDocComment();
    if (comment != null) {
      TextRange commentTextRange = comment.getTextRange();
      addRangeElem(result, editorText, comment, commentTextRange.getEndOffset());
    }
    addRangeElem(result, editorText, nameId, end);
    addRangeElem(result, editorText, field.getTypeElement(), nameRange.getEndOffset());
    addRangeElem(result, editorText, field.getModifierList(), fieldRange.getEndOffset());
    result.addAll(expandToWholeLine(editorText, fieldRange));
    return result;
  }
}
