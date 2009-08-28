package com.intellij.refactoring.safeDelete.usageInfo;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;

/**
 * @author dsl
 */
public class SafeDeleteReferenceSimpleDeleteUsageInfo extends SafeDeleteReferenceUsageInfo {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.safeDelete.usageInfo.SafeDeleteReferenceSimpleDeleteUsageInfo");
  public SafeDeleteReferenceSimpleDeleteUsageInfo(PsiElement element, PsiElement referencedElement, boolean isSafeDelete) {
    super(element, referencedElement, isSafeDelete);
  }

  public SafeDeleteReferenceSimpleDeleteUsageInfo(PsiElement element, PsiElement referencedElement,
                                                  int startOffset, int endOffset, boolean isNonCodeUsage, boolean isSafeDelete) {
    super(element, referencedElement, startOffset, endOffset, isNonCodeUsage, isSafeDelete);
  }

  public void deleteElement() throws IncorrectOperationException {
    if(isSafeDelete()) {
      PsiElement element = getElement();
      LOG.assertTrue(element != null);
      element.delete();
    }
  }
}
