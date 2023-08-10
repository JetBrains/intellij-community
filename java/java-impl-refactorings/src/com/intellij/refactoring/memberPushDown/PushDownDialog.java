/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

  @Nullable
  @Override
  protected String getHelpId() {
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
