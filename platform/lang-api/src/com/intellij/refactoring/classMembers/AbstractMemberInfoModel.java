package com.intellij.refactoring.classMembers;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * Nikolay.Tropin
 * 8/23/13
 */
public abstract class AbstractMemberInfoModel<T extends PsiElement, M extends MemberInfoBase<T>> implements MemberInfoModel<T, M> {

  @Override
  public boolean isMemberEnabled(M member) {
    return true;
  }

  @Override
  public boolean isCheckedWhenDisabled(M member) {
    return false;
  }

  @Override
  public boolean isAbstractEnabled(M member) {
    return false;
  }

  @Override
  public boolean isAbstractWhenDisabled(M member) {
    return false;
  }

  @Override
  public Boolean isFixedAbstract(M member) {
    return null;
  }

  @Override
  public int checkForProblems(@NotNull M member) {
    return OK;
  }

  @Override
  public String getTooltipText(M member) {
    return null;
  }

  @Override
  public void memberInfoChanged(MemberInfoChange<T, M> event) {
  }
}
