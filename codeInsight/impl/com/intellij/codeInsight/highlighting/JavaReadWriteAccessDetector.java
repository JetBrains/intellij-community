package com.intellij.codeInsight.highlighting;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.util.PsiUtil;

/**
 * @author yole
 */
public class JavaReadWriteAccessDetector implements ReadWriteAccessDetector {
  public boolean isReadWriteAccessible(final PsiElement element) {
    return element instanceof PsiVariable;
  }

  public boolean isWriteAccess(final PsiElement referencedElement, final PsiReference reference) {
    PsiElement refElement = reference.getElement();
    return refElement instanceof PsiExpression && PsiUtil.isAccessedForWriting((PsiExpression) refElement);
  }
}
