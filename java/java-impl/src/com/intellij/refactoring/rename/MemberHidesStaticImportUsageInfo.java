package com.intellij.refactoring.rename;

import com.intellij.psi.PsiElement;
import com.intellij.refactoring.util.RelatedUsageInfo;

/**
 * @author yole
 */
public class MemberHidesStaticImportUsageInfo extends RelatedUsageInfo {
  public MemberHidesStaticImportUsageInfo(PsiElement element, PsiElement referencedElement, PsiElement collidingElement) {
    super(element, null, referencedElement, collidingElement);
  }
}
