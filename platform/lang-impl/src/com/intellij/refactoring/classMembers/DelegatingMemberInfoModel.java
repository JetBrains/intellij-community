// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.refactoring.classMembers;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DelegatingMemberInfoModel<T extends PsiElement, M extends MemberInfoBase<T>> implements MemberInfoModel<T, M> {
  private MemberInfoModel<T, M> myDelegatingTarget;

  public DelegatingMemberInfoModel(MemberInfoModel<T, M> delegatingTarget) {
    myDelegatingTarget = delegatingTarget;
  }

  protected DelegatingMemberInfoModel() {

  }

  public MemberInfoModel getDelegatingTarget() {
    return myDelegatingTarget;
  }

  @Override
  public boolean isMemberEnabled(M member) {
    return myDelegatingTarget.isMemberEnabled(member);
  }

  @Override
  public boolean isCheckedWhenDisabled(M member) {
    return myDelegatingTarget.isCheckedWhenDisabled(member);
  }

  @Override
  public boolean isAbstractEnabled(M member) {
    return myDelegatingTarget.isAbstractEnabled(member);
  }

  @Override
  public boolean isAbstractWhenDisabled(M member) {
    return myDelegatingTarget.isAbstractWhenDisabled(member);
  }

  @Override
  public int checkForProblems(@NotNull M member) {
    return myDelegatingTarget.checkForProblems(member);
  }

  @Override
  public void memberInfoChanged(@NotNull MemberInfoChange<T, M> event) {
    myDelegatingTarget.memberInfoChanged(event);
  }

  @Override
  public @Nullable Boolean isFixedAbstract(M member) {
    return myDelegatingTarget.isFixedAbstract(member);
  }

  @Override
  public String getTooltipText(M member) {
    return myDelegatingTarget.getTooltipText(member);
  }
}
