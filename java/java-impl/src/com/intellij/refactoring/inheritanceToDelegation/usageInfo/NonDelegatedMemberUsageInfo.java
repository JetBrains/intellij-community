package com.intellij.refactoring.inheritanceToDelegation.usageInfo;

import com.intellij.psi.*;

/**
 * @author dsl
 */
public class NonDelegatedMemberUsageInfo extends InheritanceToDelegationUsageInfo {
  public PsiElement nonDelegatedMember;

  public NonDelegatedMemberUsageInfo(PsiElement element, PsiElement nonDelegatedMember,
                                     FieldAccessibility delegateFieldVisibility) {
    super(element, delegateFieldVisibility);
    this.nonDelegatedMember = nonDelegatedMember;
  }
}
