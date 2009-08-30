package com.intellij.refactoring.inheritanceToDelegation.usageInfo;

import com.intellij.psi.*;
import com.intellij.usageView.UsageInfo;

/**
 * @author dsl
 */
public class InheritanceToDelegationUsageInfo extends UsageInfo {
  private final FieldAccessibility myDelegateFieldVisibility;

  public InheritanceToDelegationUsageInfo(PsiElement element, FieldAccessibility delegateFieldVisibility) {
    super(element);
    myDelegateFieldVisibility = delegateFieldVisibility;
  }

  public FieldAccessibility getDelegateFieldAccessible() {
    return myDelegateFieldVisibility;
  }
}
