// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.removemiddleman;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactorJBundle;
import com.intellij.refactoring.classMembers.DelegatingMemberInfoModel;
import com.intellij.refactoring.ui.MemberSelectionPanel;
import com.intellij.refactoring.ui.MemberSelectionTable;
import com.intellij.refactoring.ui.RefactoringDialog;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.List;

public class RemoveMiddlemanDialog extends RefactoringDialog {
  private final JTextField fieldNameLabel;
  private final List<MemberInfo> delegateMethods;
  private final PsiField myField;

  RemoveMiddlemanDialog(PsiField field, MemberInfo[] delegateMethods) {
    super(field.getProject(), true);
    myField = field;
    this.delegateMethods = Arrays.asList(delegateMethods);
    fieldNameLabel = new JTextField();
    fieldNameLabel.setText(
      PsiFormatUtil.formatVariable(myField, PsiFormatUtilBase.SHOW_TYPE | PsiFormatUtilBase.SHOW_NAME, PsiSubstitutor.EMPTY));
    setTitle(RefactorJBundle.message("remove.middleman.title"));
    init();
  }

  @Override
  protected String getDimensionServiceKey() {
    return "RefactorJ.RemoveMiddleman";
  }

  @Override
  protected JComponent createCenterPanel() {
    final JPanel panel = new JPanel(new BorderLayout());
    panel.setBorder(BorderFactory.createEmptyBorder(10, 0, 5, 0));
    final MemberSelectionPanel selectionPanel = new MemberSelectionPanel(
      JavaRefactoringBundle.message("remove.middleman.methods.to.inline.title"), delegateMethods,
      JavaRefactoringBundle.message("remove.middleman.column.header"));
    final MemberSelectionTable table = selectionPanel.getTable();
    table.setMemberInfoModel(new DelegatingMemberInfoModel<>(table.getMemberInfoModel()) {
      @Override
      public int checkForProblems(@NotNull final MemberInfo member) {
        return hasSuperMethods(member) ? ERROR : OK;
      }

      @Override
      public String getTooltipText(final MemberInfo member) {
        if (hasSuperMethods(member)) return JavaRefactoringBundle.message("remove.middleman.tooltip.warning");
        return super.getTooltipText(member);
      }

      private boolean hasSuperMethods(final MemberInfo member) {
        if (member.isChecked() && member.isToAbstract()) {
          final PsiMember psiMember = member.getMember();
          if (psiMember instanceof PsiMethod && ((PsiMethod)psiMember).findDeepestSuperMethods().length > 0) {
            return true;
          }
        }
        return false;
      }
    });
    panel.add(selectionPanel, BorderLayout.CENTER);
    return panel;
  }

  @Override
  protected JComponent createNorthPanel() {
    fieldNameLabel.setEditable(false);
    final JPanel sourceClassPanel = new JPanel(new BorderLayout());
    sourceClassPanel.add(new JLabel(JavaRefactoringBundle.message("delegating.field")), BorderLayout.NORTH);
    sourceClassPanel.add(fieldNameLabel, BorderLayout.CENTER);
    return sourceClassPanel;
  }

  @Override
  protected String getHelpId() {
    return HelpID.RemoveMiddleman;
  }

  @Override
  protected void doAction() {
    invokeRefactoring(new RemoveMiddlemanProcessor(myField, delegateMethods));
  }
}