package com.intellij.refactoring.inheritanceToDelegation.usageInfo;

import com.intellij.psi.*;

/**
 * @author dsl
 */
public class UnqualifiedNonDelegatedMemberUsageInfo extends NonDelegatedMemberUsageInfo{

  public UnqualifiedNonDelegatedMemberUsageInfo(PsiElement element, PsiElement nonDelegatedMember, FieldAccessibility delegateFieldVisibility) {
    super(element, nonDelegatedMember, delegateFieldVisibility);
  }
}
