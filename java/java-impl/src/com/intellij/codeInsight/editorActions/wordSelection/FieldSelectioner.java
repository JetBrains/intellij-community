package com.intellij.codeInsight.editorActions.wordSelection;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiIdentifier;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.editor.Editor;

import java.util.List;

public class FieldSelectioner extends WordSelectioner {
  public boolean canSelect(PsiElement e) {
    return e instanceof PsiField;
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

  public List<TextRange> select(PsiElement e, CharSequence editorText, int cursorOffset, Editor editor) {
    List<TextRange> result = super.select(e, editorText, cursorOffset, editor);
    final PsiField field = (PsiField)e;
    final TextRange range = field.getTextRange();
    final PsiIdentifier first = field.getNameIdentifier();
    final TextRange firstRange = first.getTextRange();
    final PsiElement last = field.getInitializer();
    final int end = last == null ? firstRange.getEndOffset() : last.getTextRange().getEndOffset();
    addRangeElem(result, editorText, first, end);
    //addRangeElem (result, editorText, field, textLength, field.getTypeElement(), end);
    addRangeElem(result, editorText, field.getModifierList(), range.getEndOffset());
    //addRangeElem (result, editorText, field, textLength, field.getDocComment(), end);
    result.addAll(expandToWholeLine(editorText, range));
    return result;
  }
}
