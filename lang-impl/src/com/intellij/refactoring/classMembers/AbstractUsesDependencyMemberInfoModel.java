package com.intellij.refactoring.classMembers;

import com.intellij.psi.NavigatablePsiElement;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dennis.Ushakov
 */
public abstract class AbstractUsesDependencyMemberInfoModel<T extends NavigatablePsiElement, C extends PsiElement, M extends MemberInfoBase<T>> extends DependencyMemberInfoModel<T, M> {
  protected final C myClass;

  public AbstractUsesDependencyMemberInfoModel(C aClass, C superClass, boolean recursive) {
    super(new UsesMemberDependencyGraph<T, C, M>(aClass, superClass, recursive), ERROR);
    myClass = aClass;
    setTooltipProvider(new MemberInfoTooltipManager.TooltipProvider<T, M>() {
      public String getTooltip(M memberInfo) {
        return ((UsesMemberDependencyGraph<T, C, M>) myMemberDependencyGraph).getElementTooltip(memberInfo.getMember());
      }
    });
  }

  public int checkForProblems(@NotNull M memberInfo) {
    final int problem = super.checkForProblems(memberInfo);
    return doCheck(memberInfo, problem);
  }

  protected abstract int doCheck(@NotNull M memberInfo, int problem);

  public void setSuperClass(C superClass) {
    setMemberDependencyGraph(new UsesMemberDependencyGraph<T, C, M>(myClass, superClass, false));
  }

  public boolean isCheckedWhenDisabled(M member) {
    return false;
  }

  public Boolean isFixedAbstract(M member) {
    return null;
  }
}
