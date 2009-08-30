/*
 * User: anna
 * Date: 25-Aug-2008
 */
package com.intellij.refactoring.extractclass;

import com.intellij.openapi.util.Condition;
import com.intellij.psi.*;

public class BackpointerUtil {
  private BackpointerUtil() {
  }

  public static boolean isBackpointerReference(PsiExpression expression, Condition<PsiField> value) {
    if (expression instanceof PsiParenthesizedExpression) {
      final PsiExpression contents = ((PsiParenthesizedExpression)expression).getExpression();
      return isBackpointerReference(contents, value);
    }
    if (!(expression instanceof PsiReferenceExpression)) {
      return false;
    }
    final PsiReferenceExpression reference = (PsiReferenceExpression)expression;
    final PsiElement qualifier = reference.getQualifier();
    if (qualifier != null && !(qualifier instanceof PsiThisExpression)) {
      return false;
    }
    final PsiElement referent = reference.resolve();
    return referent instanceof PsiField && value.value((PsiField)referent);
  }
}