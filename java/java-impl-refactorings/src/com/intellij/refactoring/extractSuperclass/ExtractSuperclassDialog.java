/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.refactoring.extractSuperclass;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.classMembers.MemberInfoChange;
import com.intellij.refactoring.classMembers.MemberInfoModel;
import com.intellij.refactoring.memberPullUp.PullUpProcessor;
import com.intellij.refactoring.ui.MemberSelectionPanel;
import com.intellij.refactoring.util.DocCommentPolicy;
import com.intellij.refactoring.util.classMembers.InterfaceContainmentVerifier;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.refactoring.util.classMembers.UsesAndInterfacesDependencyMemberInfoModel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.List;

class ExtractSuperclassDialog extends JavaExtractSuperBaseDialog {
  private final InterfaceContainmentVerifier myContainmentVerifier = new InterfaceContainmentVerifier() {
    @Override
    public boolean checkedInterfacesContain(PsiMethod psiMethod) {
      return PullUpProcessor.checkedInterfacesContain(getMemberInfos(), psiMethod);
    }
  };

  public interface Callback {

    boolean checkConflicts(ExtractSuperclassDialog dialog);
  }
  private final Callback myCallback;

  ExtractSuperclassDialog(Project project, PsiClass sourceClass, List<MemberInfo> selectedMembers, Callback callback) {
    super(project, sourceClass, selectedMembers, ExtractSuperclassHandler.getRefactoringName());
    myCallback = callback;
    init();
  }

  InterfaceContainmentVerifier getContainmentVerifier() {
    return myContainmentVerifier;
  }

  private List<MemberInfo> getMemberInfos() {
    return myMemberInfos;
  }

  @Override
  protected String getClassNameLabelText() {
    return isExtractSuperclass()
           ? RefactoringBundle.message("superclass.name")
           : JavaRefactoringBundle.message("extractSuper.rename.original.class.to");
  }

  @Override
  protected String getPackageNameLabelText() {
    return isExtractSuperclass()
           ? RefactoringBundle.message("package.for.new.superclass")
           : RefactoringBundle.message("package.for.original.class");
  }

  @NotNull
  @Override
  protected String getEntityName() {
    return RefactoringBundle.message("ExtractSuperClass.superclass");
  }

  @Override
  protected String getTopLabelText() {
    return RefactoringBundle.message("extract.superclass.from");
  }

  @Override
  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new BorderLayout());
    final MemberSelectionPanel memberSelectionPanel = new MemberSelectionPanel(JavaRefactoringBundle.message("members.to.form.superclass.title"),
                                                                               myMemberInfos, RefactoringBundle.message("make.abstract"));
    panel.add(memberSelectionPanel, BorderLayout.CENTER);
    final MemberInfoModel<PsiMember, MemberInfo> memberInfoModel =
      new UsesAndInterfacesDependencyMemberInfoModel<>(mySourceClass, null, false, myContainmentVerifier) {
        @Override
        public Boolean isFixedAbstract(MemberInfo member) {
          return Boolean.TRUE;
        }
      };
    memberInfoModel.memberInfoChanged(new MemberInfoChange<>(myMemberInfos));
    memberSelectionPanel.getTable().setMemberInfoModel(memberInfoModel);
    memberSelectionPanel.getTable().addMemberInfoChangeListener(memberInfoModel);

    panel.add(myDocCommentPanel, BorderLayout.EAST);

    return panel;
  }

  @Override
  protected String getDocCommentPanelName() {
    return JavaRefactoringBundle.message("javadoc.for.abstracts");
  }

  @Override
  protected String getExtractedSuperNameNotSpecifiedMessage() {
    return RefactoringBundle.message("no.superclass.name.specified");
  }

  @Override
  protected boolean checkConflicts() {
    return myCallback.checkConflicts(this);
  }

  @Override
  protected int getDocCommentPolicySetting() {
    return JavaRefactoringSettings.getInstance().EXTRACT_SUPERCLASS_JAVADOC;
  }

  @Override
  protected void setDocCommentPolicySetting(int policy) {
    JavaRefactoringSettings.getInstance().EXTRACT_SUPERCLASS_JAVADOC = policy;
  }


  @Override
  protected ExtractSuperBaseProcessor createProcessor() {
    return new ExtractSuperClassProcessor(myProject, getTargetDirectory(), getExtractedSuperName(),
                                          mySourceClass, getSelectedMemberInfos().toArray(new MemberInfo[0]), false,
                                          new DocCommentPolicy(getDocCommentPolicy()));
  }

  @Override
  protected String getHelpId() {
    return HelpID.EXTRACT_SUPERCLASS;
  }
}
