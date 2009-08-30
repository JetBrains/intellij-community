/*
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: 09.07.2002
 * Time: 15:18:10
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.refactoring.util.classMembers;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMember;
import com.intellij.refactoring.classMembers.DependencyMemberInfoModel;
import com.intellij.refactoring.classMembers.MemberInfoTooltipManager;

public class InterfaceDependencyMemberInfoModel extends DependencyMemberInfoModel<PsiMember, MemberInfo> {

  public InterfaceDependencyMemberInfoModel(PsiClass aClass) {
    super(new InterfaceMemberDependencyGraph(aClass), WARNING);
    setTooltipProvider(new MemberInfoTooltipManager.TooltipProvider<PsiMember, MemberInfo>() {
      public String getTooltip(MemberInfo memberInfo) {
        return ((InterfaceMemberDependencyGraph) myMemberDependencyGraph).getElementTooltip(memberInfo.getMember());
      }
    });
  }

  public boolean isCheckedWhenDisabled(MemberInfo member) {
    return false;
  }

  public Boolean isFixedAbstract(MemberInfo member) {
    return null;
  }
}
