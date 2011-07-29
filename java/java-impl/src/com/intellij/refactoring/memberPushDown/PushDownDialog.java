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

import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMember;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.classMembers.MemberInfoChange;
import com.intellij.refactoring.classMembers.MemberInfoModel;
import com.intellij.refactoring.classMembers.UsedByDependencyMemberInfoModel;
import com.intellij.refactoring.ui.DocCommentPanel;
import com.intellij.refactoring.ui.MemberSelectionPanel;
import com.intellij.refactoring.ui.RefactoringDialog;
import com.intellij.refactoring.util.DocCommentPolicy;
import com.intellij.refactoring.util.classMembers.MemberInfo;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PushDownDialog extends RefactoringDialog {
  private final List<MemberInfo> myMemberInfos;
  private final PsiClass myClass;
  private DocCommentPanel myJavaDocPanel;
  private MemberInfoModel<PsiMember, MemberInfo> myMemberInfoModel;

  public PushDownDialog(Project project, MemberInfo[] memberInfos, PsiClass aClass) {
    super(project, true);
    myMemberInfos = Arrays.asList(memberInfos);
    myClass = aClass;

    setTitle(JavaPushDownHandler.REFACTORING_NAME);

    init();
  }

  public int getJavaDocPolicy() {
    return myJavaDocPanel.getPolicy();
  }

  public MemberInfo[] getSelectedMemberInfos() {
    ArrayList<MemberInfo> list = new ArrayList<MemberInfo>(myMemberInfos.size());
    for (MemberInfo info : myMemberInfos) {
      if (info.isChecked() && myMemberInfoModel.isMemberEnabled(info)) {
        list.add(info);
      }
    }
    return list.toArray(new MemberInfo[list.size()]);
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(HelpID.MEMBERS_PUSH_DOWN);
  }

  protected String getDimensionServiceKey() {
    return "#com.intellij.refactoring.memberPushDown.PushDownDialog";
  }

  protected JComponent createNorthPanel() {
    GridBagConstraints gbConstraints = new GridBagConstraints();

    JPanel panel = new JPanel(new GridBagLayout());

    gbConstraints.insets = new Insets(4, 0, 10, 8);
    gbConstraints.weighty = 1;
    gbConstraints.weightx = 1;
    gbConstraints.gridy = 0;
    gbConstraints.gridwidth = GridBagConstraints.REMAINDER;
    gbConstraints.fill = GridBagConstraints.BOTH;
    gbConstraints.anchor = GridBagConstraints.WEST;
    panel.add(new JLabel(RefactoringBundle.message("push.members.from.0.down.label",
                                                   myClass.getQualifiedName())), gbConstraints);
    return panel;
  }

  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new BorderLayout());
    final MemberSelectionPanel memberSelectionPanel = new MemberSelectionPanel(
      RefactoringBundle.message("members.to.be.pushed.down.panel.title"),
      myMemberInfos,
      RefactoringBundle.message("keep.abstract.column.header"));
    panel.add(memberSelectionPanel, BorderLayout.CENTER);

    myMemberInfoModel = new MyMemberInfoModel();
    myMemberInfoModel.memberInfoChanged(new MemberInfoChange<PsiMember, MemberInfo>(myMemberInfos));
    memberSelectionPanel.getTable().setMemberInfoModel(myMemberInfoModel);
    memberSelectionPanel.getTable().addMemberInfoChangeListener(myMemberInfoModel);


    myJavaDocPanel = new DocCommentPanel(RefactoringBundle.message("push.down.javadoc.panel.title"));
    myJavaDocPanel.setPolicy(JavaRefactoringSettings.getInstance().PULL_UP_MEMBERS_JAVADOC);
    panel.add(myJavaDocPanel, BorderLayout.EAST);
    return panel;
  }

  protected void doAction() {
    if(!isOKActionEnabled()) return;

    JavaRefactoringSettings.getInstance().PUSH_DOWN_PREVIEW_USAGES = isPreviewUsages();

    invokeRefactoring (new PushDownProcessor(
            getProject(), getSelectedMemberInfos(), myClass,
            new DocCommentPolicy(getJavaDocPolicy())));
  }

  private class MyMemberInfoModel extends UsedByDependencyMemberInfoModel<PsiMember, PsiClass, MemberInfo> {
    public MyMemberInfoModel() {
      super(myClass);
    }
  }
}
