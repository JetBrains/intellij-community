// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.extractInterface;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.JavaFeature;
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
import org.jetbrains.annotations.Unmodifiable;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Set;

class ExtractInterfaceDialog extends JavaExtractSuperBaseDialog {

  ExtractInterfaceDialog(Project project, PsiClass sourceClass, @Unmodifiable Set<PsiElement> selectedMembers) {
    super(project, sourceClass, collectMembers(sourceClass, selectedMembers), ExtractInterfaceHandler.getRefactoringName());
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

  private static List<MemberInfo> collectMembers(PsiClass c, Set<PsiElement> selectedMembers) {
    List<MemberInfo> infos = MemberInfo.extractClassMembers(c, new MemberInfoBase.Filter<>() {
      @Override
      public boolean includeMember(PsiMember element) {
        if (element instanceof PsiMethod) {
          if (PsiUtil.isLanguageLevel9OrHigher(element)) {
            return true;
          }
          return element.hasModifierProperty(PsiModifier.PUBLIC)
                 && (PsiUtil.isAvailable(JavaFeature.STATIC_INTERFACE_CALLS, element) || !element.hasModifierProperty(PsiModifier.STATIC));
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
    for (MemberInfo info : infos) {
      if (selectedMembers.contains(info.getMember())) {
        info.setChecked(true);
      }
    }
    return infos;
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

  @Override
  protected @NotNull String getEntityName() {
    return RefactoringBundle.message("extractSuperInterface.interface");
  }

  @Override
  protected String getTopLabelText() {
    return RefactoringBundle.message("extract.interface.from");
  }

  @Override
  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new BorderLayout());
    String title = JavaRefactoringBundle.message("members.to.form.interface.title");
    final MemberSelectionPanel memberSelectionPanel = new MemberSelectionPanel(title, myMemberInfos, RefactoringBundle.message("make.abstract"));
    memberSelectionPanel.getTable()
      .setMemberInfoModel(new DelegatingMemberInfoModel<>(memberSelectionPanel.getTable().getMemberInfoModel()) {
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
