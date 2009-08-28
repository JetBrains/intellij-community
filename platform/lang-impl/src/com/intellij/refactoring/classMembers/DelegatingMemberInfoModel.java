/*
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: 09.07.2002
 * Time: 15:44:58
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.refactoring.classMembers;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

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

  public boolean isMemberEnabled(M member) {
    return myDelegatingTarget.isMemberEnabled(member);
  }

  public boolean isCheckedWhenDisabled(M member) {
    return myDelegatingTarget.isCheckedWhenDisabled(member);
  }

  public boolean isAbstractEnabled(M member) {
    return myDelegatingTarget.isAbstractEnabled(member);
  }

  public boolean isAbstractWhenDisabled(M member) {
    return myDelegatingTarget.isAbstractWhenDisabled(member);
  }

  public int checkForProblems(@NotNull M member) {
    return myDelegatingTarget.checkForProblems(member);
  }

  public void memberInfoChanged(MemberInfoChange<T, M> event) {
    myDelegatingTarget.memberInfoChanged(event);
  }

  public Boolean isFixedAbstract(M member) {
    return myDelegatingTarget.isFixedAbstract(member);
  }

  public String getTooltipText(M member) {
    return myDelegatingTarget.getTooltipText(member);
  }
}
