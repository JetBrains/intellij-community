// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.memberPushDown;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMember;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.classMembers.ANDCombinedMemberInfoModel;
import com.intellij.refactoring.classMembers.DelegatingMemberInfoModel;
import com.intellij.refactoring.classMembers.MemberInfoModel;
import com.intellij.refactoring.classMembers.UsedByDependencyMemberInfoModel;
import com.intellij.refactoring.ui.MemberSelectionPanel;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.refactoring.util.classMembers.UsesDependencyMemberInfoModel;
import org.jetbrains.annotations.Nullable;

public class PushDownDialog extends AbstractPushDownDialog<MemberInfo, PsiMember, PsiClass> {
  public PushDownDialog(Project project, MemberInfo[] memberInfos, PsiClass aClass) {
    super(project, memberInfos, aClass);
  }

  @Override
  protected MemberInfoModel<PsiMember, MemberInfo> createMemberInfoModel() {
    return new MyMemberInfoModel();
  }

  @Override
  protected MemberSelectionPanel createMemberInfoPanel() {
    return new MemberSelectionPanel(
      RefactoringBundle.message("members.to.be.pushed.down.panel.title"),
      getMemberInfos(),
      RefactoringBundle.message("keep.abstract.column.header"));
  }

  @Override
  protected int getDocCommentPolicy() {
    return JavaRefactoringSettings.getInstance().PULL_UP_MEMBERS_JAVADOC;
  }

  @Override
  protected @Nullable String getHelpId() {
    return HelpID.MEMBERS_PUSH_DOWN;
  }

  private class MyMemberInfoModel extends DelegatingMemberInfoModel<PsiMember,MemberInfo> {
    MyMemberInfoModel() {
      super(new ANDCombinedMemberInfoModel<>(
        new UsesDependencyMemberInfoModel<>(getSourceClass(), null, false),
        new UsedByDependencyMemberInfoModel<>(getSourceClass()))
      );
    }
  }
}
