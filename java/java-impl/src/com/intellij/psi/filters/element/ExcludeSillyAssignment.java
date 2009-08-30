package com.intellij.psi.filters.element;

import com.intellij.psi.*;
import com.intellij.psi.filters.ElementFilter;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 06.01.2004
 * Time: 17:59:58
 * To change this template use Options | File Templates.
 */
public class ExcludeSillyAssignment implements ElementFilter {
  public boolean isAcceptable(Object element, PsiElement context) {
    if(!(element instanceof PsiElement)) return true;

    PsiElement each = context;
    while (each != null && !(each instanceof PsiFile)) {
      if (each instanceof PsiExpressionList || each instanceof PsiPrefixExpression || each instanceof PsiBinaryExpression) {
        return true;
      }

      if (each instanceof PsiAssignmentExpression) {
        final PsiExpression left = ((PsiAssignmentExpression)each).getLExpression();
        if (left instanceof PsiReferenceExpression) {
          final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)left;
          if (referenceExpression.isQualified()) return true;

          return !referenceExpression.isReferenceTo((PsiElement)element);
        }
        return true;
      }

      each = each.getContext();
    }

    return true;
  }

  public boolean isClassAcceptable(Class hintClass) {
    return true;
  }
}
