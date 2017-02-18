/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.testIntegration.createTest;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.template.Template;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.ui.MemberSelectionTable;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.testIntegration.TestFramework;
import com.intellij.testIntegration.TestIntegrationUtils;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MissedTestsDialog extends DialogWrapper {
  private final PsiClass mySourceClass;
  private final PsiClass myTestClass;
  private final TestFramework myDescriptor;
  private MemberSelectionTable myTable;
  private final JBCheckBox myIncludeInheritedCb = new JBCheckBox(CodeInsightBundle.message("intention.create.test.dialog.show.inherited"));

  public MissedTestsDialog(@Nullable Project project, PsiClass sourceClass, PsiClass testClass, TestFramework descriptor) {
    super(project, true);
    mySourceClass = sourceClass;
    myTestClass = testClass;
    myDescriptor = descriptor;
    setTitle("Create Missed Tests");
    init();
  }

  public Collection<MemberInfo> getSelectedMethods() {
    return myTable.getSelectedMemberInfos();
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    final List<MemberInfo> info = TestIntegrationUtils.extractClassMethods(mySourceClass, false);

    disableMethodsWithTests(info);

    final JPanel panel = new JPanel(new BorderLayout());
    myTable = new MemberSelectionTable(info, null);
    panel.add(ScrollPaneFactory.createScrollPane(myTable), BorderLayout.CENTER);
    panel.add(myIncludeInheritedCb, BorderLayout.NORTH);
    myIncludeInheritedCb.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        updateMethodsTable();
      }
    });
    return panel;
  }

  private void disableMethodsWithTests(List<MemberInfo> info) {
    final Set<String> existingNames = new HashSet<>();
    final String prefix = getTestPrefix(existingNames);

    existingNames.addAll(ContainerUtil.map(myTestClass.getMethods(), method -> StringUtil.decapitalize(StringUtil.trimStart(method.getName(), prefix))));


    for (MemberInfo memberInfo : info) {
      final PsiMember member = memberInfo.getMember();
      memberInfo.setChecked(!(member instanceof PsiMethod && existingNames.contains(member.getName())));
    }
  }

  private String getTestPrefix(Set<String> existingNames) {
    final Template template = TestIntegrationUtils.createTestMethodTemplate(TestIntegrationUtils.MethodKind.TEST, myDescriptor, myTestClass, null, true, existingNames);
    try {
      return JavaPsiFacade.getElementFactory(myTestClass.getProject()).createMethodFromText(template.getTemplateText(), myTestClass).getName();
    }
    catch (IncorrectOperationException e) {
      return "";
    }
  }

  private void updateMethodsTable() {
    List<MemberInfo> infos = TestIntegrationUtils.extractClassMethods(mySourceClass, myIncludeInheritedCb.isSelected());

    Set<PsiMember> selectedMethods = new HashSet<>();
    for (MemberInfo each : myTable.getSelectedMemberInfos()) {
      selectedMethods.add(each.getMember());
    }

    for (MemberInfo each : infos) {
      each.setChecked(selectedMethods.contains(each.getMember()));
    }

    myTable.setMemberInfos(infos);
  }
}
