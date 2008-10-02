package com.intellij.codeInsight.highlighting;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PropertyUtil;

/**
 * @author yole
 */
public class JavaReadWriteAccessDetector extends ReadWriteAccessDetector {
  public boolean isReadWriteAccessible(final PsiElement element) {
    return element instanceof PsiVariable && !(element instanceof ImplicitVariable);
  }

  public boolean isDeclarationWriteAccess(final PsiElement element) {
    return element instanceof PsiVariable && ((PsiVariable) element).getInitializer() != null;
  }

  public Access getReferenceAccess(final PsiElement referencedElement, final PsiReference reference) {
    return getExpressionAccess(reference.getElement());
  }

  public Access getExpressionAccess(final PsiElement expression) {
    if (!(expression instanceof PsiExpression)) return Access.Read;
    PsiExpression expr = (PsiExpression) expression;
    boolean readAccess = PsiUtil.isAccessedForReading(expr);
    boolean writeAccess = PsiUtil.isAccessedForWriting(expr);
    if (!writeAccess && expr instanceof PsiReferenceExpression) {
      //when searching usages of fields, should show all found setters as a "only write usage"
      PsiElement actualReferee = ((PsiReferenceExpression) expr).resolve();
      if (actualReferee instanceof PsiMethod && PropertyUtil.isSimplePropertySetter((PsiMethod)actualReferee)) {
        writeAccess = true;
        readAccess = false;
      }
    }
    if (writeAccess && readAccess) return Access.ReadWrite;
    return writeAccess ? Access.Write : Access.Read;
  }
}
