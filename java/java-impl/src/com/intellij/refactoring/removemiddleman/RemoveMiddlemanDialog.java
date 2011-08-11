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
package com.intellij.refactoring.removemiddleman;

import com.intellij.openapi.help.HelpManager;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.util.PsiFormatUtil;
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

@SuppressWarnings({"OverridableMethodCallInConstructor"})
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
      PsiFormatUtil.formatVariable(myField, PsiFormatUtil.SHOW_TYPE | PsiFormatUtil.SHOW_NAME, PsiSubstitutor.EMPTY));
    setTitle(RefactorJBundle.message("remove.middleman.title"));
    init();
  }

  protected String getDimensionServiceKey() {
    return "RefactorJ.RemoveMiddleman";
  }


  protected JComponent createCenterPanel() {
    final JPanel panel = new JPanel(new BorderLayout());
    panel.setBorder(BorderFactory.createEmptyBorder(10, 0, 5, 0));
    final MemberSelectionPanel selectionPanel = new MemberSelectionPanel("&Methods to inline", delegateMethods, "Delete");
    final MemberSelectionTable table = selectionPanel.getTable();
    table.setMemberInfoModel(new DelegatingMemberInfoModel<PsiMember, MemberInfo>(table.getMemberInfoModel()) {
      @Override
      public int checkForProblems(@NotNull final MemberInfo member) {
        return hasSuperMethods(member) ? ERROR : OK;
      }

      @Override
      public String getTooltipText(final MemberInfo member) {
        if (hasSuperMethods(member)) return "Deletion will break type hierarchy";
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

  protected JComponent createNorthPanel() {
    fieldNameLabel.setEditable(false);
    final JPanel sourceClassPanel = new JPanel(new BorderLayout());
    sourceClassPanel.add(new JLabel("Delegating field"), BorderLayout.NORTH);
    sourceClassPanel.add(fieldNameLabel, BorderLayout.CENTER);
    return sourceClassPanel;
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(HelpID.RemoveMiddleman);
  }

  protected void doAction() {
    invokeRefactoring(new RemoveMiddlemanProcessor(myField, delegateMethods));
  }
}
