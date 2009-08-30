package com.intellij.refactoring.inheritanceToDelegation.usageInfo;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;

/**
 * @author dsl
 */
public class ObjectUpcastedUsageInfo extends UpcastedUsageInfo {
  public ObjectUpcastedUsageInfo(PsiElement element, PsiClass aClass, FieldAccessibility delegateFieldVisibility) {
    super(element, aClass, delegateFieldVisibility);
  }
}
