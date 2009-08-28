package com.intellij.refactoring.classMembers;

import com.intellij.psi.NavigatablePsiElement;
import com.intellij.psi.PsiElement;

/**
 * @author dsl
 */
public class UsedByDependencyMemberInfoModel<T extends NavigatablePsiElement, C extends PsiElement, M extends MemberInfoBase<T>> extends DependencyMemberInfoModel<T, M> {

  public UsedByDependencyMemberInfoModel(C aClass) {
    super(new UsedByMemberDependencyGraph<T, C, M>(aClass), ERROR);
    setTooltipProvider(new MemberInfoTooltipManager.TooltipProvider<T, M>() {
      public String getTooltip(M memberInfo) {
        return ((UsedByMemberDependencyGraph<T, C, M>) myMemberDependencyGraph).getElementTooltip(memberInfo.getMember());
      }
    });
  }

  public boolean isCheckedWhenDisabled(M member) {
    return false;
  }

  public Boolean isFixedAbstract(M member) {
    return null;
  }
}
