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
package com.intellij.refactoring.extractInterface;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.classMembers.DelegatingMemberInfoModel;
import com.intellij.refactoring.classMembers.MemberInfoBase;
import com.intellij.refactoring.extractSuperclass.ExtractSuperBaseProcessor;
import com.intellij.refactoring.extractSuperclass.JavaExtractSuperBaseDialog;
import com.intellij.refactoring.ui.MemberSelectionPanel;
import com.intellij.refactoring.util.DocCommentPolicy;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.List;

class ExtractInterfaceDialog extends JavaExtractSuperBaseDialog {

  ExtractInterfaceDialog(Project project, PsiClass sourceClass) {
    super(project, sourceClass, collectMembers(sourceClass), ExtractInterfaceHandler.getRefactoringName());
    for (MemberInfo memberInfo : myMemberInfos) {
      final PsiMember member = memberInfo.getMember();
      if (member instanceof PsiMethod &&
          (member.hasModifierProperty(PsiModifier.STATIC) || member.hasModifierProperty(PsiModifier.PRIVATE))) {
        continue;
      }
      memberInfo.setToAbstract(true);
    }
    init();
  }

  private static List<MemberInfo> collectMembers(PsiClass c) {
    return MemberInfo.extractClassMembers(c, new MemberInfoBase.Filter<PsiMember>() {
      @Override
      public boolean includeMember(PsiMember element) {
        if (element instanceof PsiMethod) {
          if (PsiUtil.isLanguageLevel9OrHigher(element)) {
            return true;
          }
          return element.hasModifierProperty(PsiModifier.PUBLIC)
                 && (PsiUtil.isLanguageLevel8OrHigher(element) || !element.hasModifierProperty(PsiModifier.STATIC));
        }
        else if (element instanceof PsiField && !(element instanceof PsiEnumConstant)) {
          return element.hasModifierProperty(PsiModifier.FINAL)
                 && element.hasModifierProperty(PsiModifier.STATIC)
                 && element.hasModifierProperty(PsiModifier.PUBLIC);
        }
        else if (element instanceof PsiClass) {
          return ((PsiClass)element).isInterface() || element.hasModifierProperty(PsiModifier.STATIC);
        }
        return false;
      }
    }, true);
  }

  @Override
  protected String getClassNameLabelText() {
    return isExtractSuperclass()
           ? RefactoringBundle.message("interface.name.prompt")
           : RefactoringBundle.message("rename.implementation.class.to");
  }

  @Override
  protected String getPackageNameLabelText() {
    return isExtractSuperclass()
           ? RefactoringBundle.message("package.for.new.interface")
           : RefactoringBundle.message("package.for.original.class");
  }

  @NotNull
  @Override
  protected String getEntityName() {
    return RefactoringBundle.message("extractSuperInterface.interface");
  }

  @Override
  protected String getTopLabelText() {
    return RefactoringBundle.message("extract.interface.from");
  }

  @Override
  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new BorderLayout());
    final MemberSelectionPanel memberSelectionPanel = new MemberSelectionPanel(RefactoringBundle.message("members.to.form.interface"),
                                                                               myMemberInfos, RefactoringBundle.message("make.abstract"));
    memberSelectionPanel.getTable()
      .setMemberInfoModel(new DelegatingMemberInfoModel<PsiMember, MemberInfo>(memberSelectionPanel.getTable().getMemberInfoModel()) {
        @Override
        public Boolean isFixedAbstract(MemberInfo member) {
          return Boolean.TRUE;
        }
      });
    panel.add(memberSelectionPanel, BorderLayout.CENTER);

    panel.add(myDocCommentPanel, BorderLayout.EAST);

    return panel;
  }

  @Override
  protected String getDocCommentPanelName() {
    return JavaRefactoringBundle.message("extractSuperInterface.javadoc");
  }

  @Override
  protected String getExtractedSuperNameNotSpecifiedMessage() {
    return RefactoringBundle.message("no.interface.name.specified");
  }

  @Override
  protected int getDocCommentPolicySetting() {
    return JavaRefactoringSettings.getInstance().EXTRACT_INTERFACE_JAVADOC;
  }

  @Override
  protected void setDocCommentPolicySetting(int policy) {
    JavaRefactoringSettings.getInstance().EXTRACT_INTERFACE_JAVADOC = policy;
  }

  @Override
  protected ExtractSuperBaseProcessor createProcessor() {
    return new ExtractInterfaceProcessor(myProject, false, getTargetDirectory(), getExtractedSuperName(),
                                         mySourceClass, getSelectedMemberInfos().toArray(new MemberInfo[0]),
                                         new DocCommentPolicy(getDocCommentPolicy()));
  }

  @Override
  protected String getHelpId() {
    return HelpID.EXTRACT_INTERFACE;
  }
}
