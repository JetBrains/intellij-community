// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.refactoring.classMembers;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public final class ANDCombinedMemberInfoModel<T extends PsiElement, M extends MemberInfoBase<T>> implements MemberInfoModel<T, M> {
  private final MemberInfoModel<T, M> myModel1;
  private final MemberInfoModel<T, M> myModel2;
  private final MemberInfoTooltipManager<T, M> myTooltipManager =
    new MemberInfoTooltipManager<>(new MemberInfoTooltipManager.TooltipProvider<>() {
      @Override
      public String getTooltip(M memberInfo) {
        final String tooltipText1 = myModel1.getTooltipText(memberInfo);
        if (tooltipText1 != null) return tooltipText1;
        return myModel2.getTooltipText(memberInfo);
      }
    });


  public ANDCombinedMemberInfoModel(MemberInfoModel<T, M> model1, MemberInfoModel<T, M> model2) {
    myModel1 = model1;
    myModel2 = model2;
  }

  @Override
  public boolean isMemberEnabled(M member) {
    return myModel1.isMemberEnabled(member) && myModel2.isMemberEnabled(member);
  }

  @Override
  public boolean isCheckedWhenDisabled(M member) {
    return myModel1.isCheckedWhenDisabled(member) && myModel2.isCheckedWhenDisabled(member);
  }

  @Override
  public boolean isAbstractEnabled(M member) {
    return myModel1.isAbstractEnabled(member) && myModel2.isAbstractEnabled(member);
  }

  @Override
  public boolean isAbstractWhenDisabled(M member) {
    return myModel1.isAbstractWhenDisabled(member) && myModel2.isAbstractWhenDisabled(member);
  }

  @Override
  public int checkForProblems(@NotNull M member) {
    return Math.max(myModel1.checkForProblems(member), myModel2.checkForProblems(member));
  }

  @Override
  public void memberInfoChanged(@NotNull MemberInfoChange<T, M> event) {
    myTooltipManager.invalidate();
    myModel1.memberInfoChanged(event);
    myModel2.memberInfoChanged(event);
  }

  @Override
  public Boolean isFixedAbstract(M member) {
    final Boolean fixedAbstract1 = myModel1.isFixedAbstract(member);
    if(fixedAbstract1 == null) return null;
    if(fixedAbstract1.equals(myModel2.isFixedAbstract(member))) return fixedAbstract1;
    return null;
  }

  public MemberInfoModel<T, M> getModel1() {
    return myModel1;
  }

  public MemberInfoModel<T, M> getModel2() {
    return myModel2;
  }

  @Override
  public String getTooltipText(M member) {
    return myTooltipManager.getTooltip(member);
  }
}
