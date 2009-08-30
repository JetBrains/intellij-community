package com.intellij.refactoring.extractSuperclass;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.refactoring.util.MoveRenameUsageInfo;

/**
 * @author dsl
 */
public class BindToOldUsageInfo extends MoveRenameUsageInfo {
  public BindToOldUsageInfo(PsiElement element, PsiReference reference, PsiClass referencedElement) {
    super(element, reference, referencedElement);
  }
}
