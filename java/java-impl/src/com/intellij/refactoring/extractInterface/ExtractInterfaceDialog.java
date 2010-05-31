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

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.classMembers.DelegatingMemberInfoModel;
import com.intellij.refactoring.classMembers.MemberInfoBase;
import com.intellij.refactoring.extractSuperclass.ExtractSuperBaseDialog;
import com.intellij.refactoring.extractSuperclass.ExtractSuperBaseProcessor;
import com.intellij.refactoring.ui.MemberSelectionPanel;
import com.intellij.refactoring.util.DocCommentPolicy;
import com.intellij.refactoring.util.classMembers.MemberInfo;

import javax.swing.*;
import java.awt.*;
import java.util.List;

class ExtractInterfaceDialog extends ExtractSuperBaseDialog {
  private JLabel myInterfaceNameLabel;
  private JLabel myPackageLabel;

  public ExtractInterfaceDialog(Project project, PsiClass sourceClass) {
    super(project, sourceClass, collectMembers(sourceClass), ExtractInterfaceHandler.REFACTORING_NAME);
    init();
  }

  private static List<MemberInfo> collectMembers(PsiClass c) {
    return MemberInfo.extractClassMembers(c, new MemberInfoBase.Filter<PsiMember>() {
      public boolean includeMember(PsiMember element) {
        if (element instanceof PsiMethod) {
          return element.hasModifierProperty(PsiModifier.PUBLIC)
                 && !element.hasModifierProperty(PsiModifier.STATIC);
        }
        else if (element instanceof PsiField) {
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

  public MemberInfo[] getSelectedMembers() {
    int[] rows = getCheckedRows();
    MemberInfo[] selectedMethods = new MemberInfo[rows.length];
    for (int idx = 0; idx < rows.length; idx++) {
      selectedMethods[idx] = myMemberInfos.get(rows[idx]);
    }
    return selectedMethods;
  }

    private int[] getCheckedRows() {
    int count = 0;
    for (MemberInfo info : myMemberInfos) {
      if (info.isChecked()) {
        count++;
      }
    }
    int[] rows = new int[count];
    int currentRow = 0;
    for (int idx = 0; idx < myMemberInfos.size(); idx++) {
      if (myMemberInfos.get(idx).isChecked()) {
        rows[currentRow++] = idx;
      }
    }
    return rows;
  }

  protected JComponent createNorthPanel() {
    Box box = Box.createVerticalBox();

    JPanel _panel = new JPanel(new BorderLayout());
    _panel.add(new JLabel(RefactoringBundle.message("extract.interface.from")), BorderLayout.NORTH);
    _panel.add(mySourceClassField, BorderLayout.CENTER);
    box.add(_panel);

    box.add(Box.createVerticalStrut(10));

    box.add(createActionComponent());

    box.add(Box.createVerticalStrut(10));

    myInterfaceNameLabel = new JLabel();
    myInterfaceNameLabel.setText(RefactoringBundle.message("interface.name.prompt"));

    _panel = new JPanel(new BorderLayout());
    _panel.add(myInterfaceNameLabel, BorderLayout.NORTH);
    _panel.add(myExtractedSuperNameField, BorderLayout.CENTER);
    box.add(_panel);
    box.add(Box.createVerticalStrut(5));

    _panel = new JPanel(new BorderLayout());
    myPackageLabel = new JLabel();
    myPackageLabel.setText(getPackageNameLabelText());

    _panel.add(myPackageLabel, BorderLayout.NORTH);
    _panel.add(myPackageNameField, BorderLayout.CENTER);
    box.add(_panel);
    box.add(Box.createVerticalStrut(10));

    JPanel panel = new JPanel(new BorderLayout());
    panel.add(box, BorderLayout.CENTER);
    return panel;
  }

  @Override
  protected void updateDialogForExtractSubclass() {
    super.updateDialogForExtractSubclass();
    myInterfaceNameLabel.setText(RefactoringBundle.message("rename.implementation.class.to"));
  }

  @Override
  protected void updateDialogForExtractSuperclass() {
    super.updateDialogForExtractSuperclass();
    myInterfaceNameLabel.setText(RefactoringBundle.message("interface.name.prompt"));
  }

  protected String getClassNameLabelText() {
    return RefactoringBundle.message("superinterface.name");
  }

  protected JLabel getClassNameLabel() {
    return myInterfaceNameLabel;
  }

  @Override
  protected String getPackageNameLabelText() {
    return isExtractSuperclass() ? RefactoringBundle.message("package.for.new.interface") : "Package name for original class";
  }

  protected JLabel getPackageNameLabel() {
    return myPackageLabel;
  }

  protected String getEntityName() {
    return RefactoringBundle.message("extractSuperInterface.interface");
  }

  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new BorderLayout());
    final MemberSelectionPanel memberSelectionPanel = new MemberSelectionPanel(RefactoringBundle.message("members.to.form.interface"),
                                                                               myMemberInfos, null);
    memberSelectionPanel.getTable().setMemberInfoModel(new DelegatingMemberInfoModel<PsiMember, MemberInfo>(memberSelectionPanel.getTable().getMemberInfoModel()) {
      public Boolean isFixedAbstract(MemberInfo member) {
        return Boolean.TRUE;
      }
    });
    panel.add(memberSelectionPanel, BorderLayout.CENTER);

    panel.add(myJavaDocPanel, BorderLayout.EAST);

    return panel;
  }

  @Override
  protected String getJavaDocPanelName() {
    return RefactoringBundle.message("extractSuperInterface.javadoc");
  }

  @Override
  protected String getExtractedSuperNameNotSpecifiedKey() {
    return RefactoringBundle.message("no.interface.name.specified");
  }

  @Override
  protected int getJavaDocPolicySetting() {
    return JavaRefactoringSettings.getInstance().EXTRACT_INTERFACE_JAVADOC;
  }

  @Override
  protected void setJavaDocPolicySetting(int policy) {
    JavaRefactoringSettings.getInstance().EXTRACT_INTERFACE_JAVADOC = policy;
  }

  @Override
  protected ExtractSuperBaseProcessor createProcessor() {
    return new ExtractInterfaceProcessor(myProject, false, getTargetDirectory(), getExtractedSuperName(),
                                         mySourceClass, getSelectedMembers(),
                                         new DocCommentPolicy(getJavaDocPolicy()));
  }

  @Override
  protected String getHelpId() {
    return HelpID.EXTRACT_INTERFACE;
  }
}
