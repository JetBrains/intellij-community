/*
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: 08.07.2002
 * Time: 17:55:02
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.refactoring.util.classMembers;

import com.intellij.psi.NavigatablePsiElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.refactoring.classMembers.MemberInfoBase;
import com.intellij.refactoring.classMembers.AbstractUsesDependencyMemberInfoModel;
import org.jetbrains.annotations.NotNull;

public class UsesDependencyMemberInfoModel<T extends NavigatablePsiElement, C extends PsiElement, M extends MemberInfoBase<T>>
  extends AbstractUsesDependencyMemberInfoModel<T,C,M> {

  public UsesDependencyMemberInfoModel(C aClass, C superClass, boolean recursive) {
    super(aClass, superClass, recursive);
  }

  @Override
  protected int doCheck(@NotNull M memberInfo, int problem) {
    final PsiElement member = memberInfo.getMember();
    if(problem == ERROR
            && member instanceof PsiModifierListOwner
            && ((PsiModifierListOwner) member).hasModifierProperty(PsiModifier.STATIC)) {
      return WARNING;
    }
    return problem;
  }

}
