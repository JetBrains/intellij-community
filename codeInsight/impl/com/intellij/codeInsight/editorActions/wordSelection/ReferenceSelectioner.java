package com.intellij.codeInsight.editorActions.wordSelection;

import com.intellij.psi.*;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.editor.Editor;

import java.util.List;
import java.util.ArrayList;

public class ReferenceSelectioner extends BasicSelectioner {
  public boolean canSelect(PsiElement e) {
    return e instanceof PsiJavaCodeReferenceElement;
  }

  public List<TextRange> select(PsiElement e, CharSequence editorText, int cursorOffset, Editor editor) {

    PsiElement endElement = e;

    while (endElement instanceof PsiJavaCodeReferenceElement && endElement.getNextSibling() != null) {
      endElement = endElement.getNextSibling();
    }

    if (!(endElement instanceof PsiJavaCodeReferenceElement) &&
        !(endElement.getPrevSibling() instanceof PsiReferenceExpression && endElement instanceof PsiExpressionList)) {
      endElement = endElement.getPrevSibling();
    }

    PsiElement element = e;
    List<TextRange> result = new ArrayList<TextRange>();
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

//      if (element instanceof PsiMethodCallExpression) {
    result.addAll(expandToWholeLine(editorText, new TextRange(element.getTextRange().getStartOffset(),
                                                              endElement.getTextRange().getEndOffset())));
//      }

    if (!(e.getParent() instanceof PsiJavaCodeReferenceElement)) {
      if (e.getNextSibling() instanceof PsiJavaToken ||
          e.getNextSibling() instanceof PsiWhiteSpace ||
          e.getNextSibling() instanceof PsiExpressionList) {
        result.addAll(super.select(e, editorText, cursorOffset, editor));
      }
    }

    return result;
  }
}
