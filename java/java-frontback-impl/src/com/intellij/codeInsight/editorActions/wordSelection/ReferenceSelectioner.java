// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions.wordSelection;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiWhiteSpace;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public final class ReferenceSelectioner extends BasicSelectioner {

  @Override
  public boolean canSelect(@NotNull PsiElement e) {
    return e instanceof PsiJavaCodeReferenceElement;
  }

  @Override
  public List<TextRange> select(@NotNull PsiElement e, @NotNull CharSequence editorText, int cursorOffset, @NotNull Editor editor) {

    PsiElement endElement = e;

    while (endElement instanceof PsiJavaCodeReferenceElement && endElement.getNextSibling() != null) {
      endElement = endElement.getNextSibling();
    }

    if (!(endElement instanceof PsiJavaCodeReferenceElement) &&
        !(endElement.getPrevSibling() instanceof PsiReferenceExpression && endElement instanceof PsiExpressionList)) {
      endElement = endElement.getPrevSibling();
    }

    PsiElement element = e;
    List<TextRange> result = new ArrayList<>();
    while (element instanceof PsiJavaCodeReferenceElement) {
      PsiElement firstChild = element.getFirstChild();

      PsiElement referenceName = ((PsiJavaCodeReferenceElement)element).getReferenceNameElement();
      if (referenceName != null) {
        result.addAll(expandToWholeLine(editorText, new TextRange(referenceName.getTextRange().getStartOffset(),
                                                                  endElement.getTextRange().getEndOffset())));
        if (endElement instanceof PsiJavaCodeReferenceElement) {
          final PsiElement endReferenceName = ((PsiJavaCodeReferenceElement)endElement).getReferenceNameElement();
          if (endReferenceName != null) {
            result.addAll(expandToWholeLine(editorText, new TextRange(referenceName.getTextRange().getStartOffset(),
                                                                      endReferenceName.getTextRange().getEndOffset())));
          }
        }
      }

      if (firstChild == null) break;
      element = firstChild;
    }

    TextRange range = new TextRange(element.getTextRange().getStartOffset(),
                                    endElement.getTextRange().getEndOffset());
    result.add(range);
    result.addAll(expandToWholeLine(editorText, range));

    if (!(e.getParent() instanceof PsiJavaCodeReferenceElement)) {
      if (e.getNextSibling() instanceof PsiJavaToken ||
          e.getNextSibling() instanceof PsiWhiteSpace ||
          e.getNextSibling() instanceof PsiExpressionList) {
        List<TextRange> select = super.select(e, editorText, cursorOffset, editor);
        if (select != null) {
          result.addAll(select);
        }
      }
    }

    return result;
  }
}
