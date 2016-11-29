/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.refactoring.inheritanceToDelegation;

import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.classMembers.MemberInfoChange;
import com.intellij.refactoring.classMembers.MemberInfoModel;
import com.intellij.refactoring.ui.ClassCellRenderer;
import com.intellij.refactoring.ui.MemberSelectionPanel;
import com.intellij.refactoring.ui.NameSuggestionsField;
import com.intellij.refactoring.ui.RefactoringDialog;
import com.intellij.refactoring.util.classMembers.InterfaceMemberDependencyGraph;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;

public class InheritanceToDelegationDialog extends RefactoringDialog {
  private final PsiClass[] mySuperClasses;
  private final PsiClass myClass;
  private final HashMap<PsiClass, Collection<MemberInfo>> myBasesToMemberInfos;

  private NameSuggestionsField myFieldNameField;
  private NameSuggestionsField myInnerClassNameField;
  private JCheckBox myCbGenerateGetter;
  private MemberSelectionPanel myMemberSelectionPanel;
  private JComboBox myClassCombo;
  private final Project myProject;
  private MyClassComboItemListener myClassComboItemListener;
  private NameSuggestionsField.DataChanged myDataChangedListener;

  public InheritanceToDelegationDialog(Project project,
                                       PsiClass aClass,
                                       PsiClass[] superClasses,
                                       HashMap<PsiClass,Collection<MemberInfo>> basesToMemberInfos) {
    super(project, true);
    myProject = project;
    myClass = aClass;
    mySuperClasses = superClasses;
    myBasesToMemberInfos = basesToMemberInfos;

    setTitle(InheritanceToDelegationHandler.REFACTORING_NAME);
    init();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myFieldNameField;
  }

  protected void dispose() {
    myInnerClassNameField.removeDataChangedListener(myDataChangedListener);
    myFieldNameField.removeDataChangedListener(myDataChangedListener);
    myClassCombo.removeItemListener(myClassComboItemListener);
    super.dispose();
  }

  @NotNull
  public String getFieldName() {
    return myFieldNameField.getEnteredName();
  }

  @Nullable
  public String getInnerClassName() {
    if (myInnerClassNameField != null) {
      return myInnerClassNameField.getEnteredName();
    }
    else {
      return null;
    }
  }

  public boolean isGenerateGetter() {
    return myCbGenerateGetter.isSelected();
  }

  @Override
  protected void canRun() throws ConfigurationException {
    final String fieldName = getFieldName();
    final PsiNameHelper helper = PsiNameHelper.getInstance(myProject);
    if (!helper.isIdentifier(fieldName)){
      throw new ConfigurationException("\'" + fieldName + "\' is invalid field name for delegation");
    }
    if (myInnerClassNameField != null) {
      final String className = myInnerClassNameField.getEnteredName();
      if (!helper.isIdentifier(className)) {
        throw new ConfigurationException("\'" + className + "\' is invalid inner class name");
      }
    }
  }

  public Collection<MemberInfo> getSelectedMemberInfos() {
    return myMemberSelectionPanel.getTable().getSelectedMemberInfos();
  }

  public PsiClass getSelectedTargetClass() {
    return (PsiClass)myClassCombo.getSelectedItem();
  }


  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(HelpID.INHERITANCE_TO_DELEGATION);
  }

  protected void doAction() {
    JavaRefactoringSettings.getInstance().INHERITANCE_TO_DELEGATION_DELEGATE_OTHER = myCbGenerateGetter.isSelected();

    final Collection<MemberInfo> selectedMemberInfos = getSelectedMemberInfos();
    final ArrayList<PsiClass> implementedInterfaces = new ArrayList<>();
    final ArrayList<PsiMethod> delegatedMethods = new ArrayList<>();

    for (MemberInfo memberInfo : selectedMemberInfos) {
      final PsiElement member = memberInfo.getMember();
      if (member instanceof PsiClass && Boolean.FALSE.equals(memberInfo.getOverrides())) {
        implementedInterfaces.add((PsiClass)member);
      }
      else if (member instanceof PsiMethod) {
        delegatedMethods.add((PsiMethod)member);
      }
    }
    invokeRefactoring(new InheritanceToDelegationProcessor(myProject, myClass,
                                                           getSelectedTargetClass(), getFieldName(),
                                                           getInnerClassName(),
                                                           implementedInterfaces.toArray(new PsiClass[implementedInterfaces.size()]),
                                                           delegatedMethods.toArray(new PsiMethod[delegatedMethods.size()]),
                                                           isGenerateGetter(), isGenerateGetter()));
  }

  protected JComponent createNorthPanel() {
    JPanel panel = new JPanel(new GridBagLayout());
    GridBagConstraints gbc = new GridBagConstraints();

    gbc.fill = GridBagConstraints.BOTH;
    gbc.anchor = GridBagConstraints.WEST;
    gbc.gridy = 0;
    gbc.gridx = 0;


    gbc.insets = JBUI.insets(4, 0, 0, 8);
    myClassCombo = new JComboBox(mySuperClasses);
    myClassCombo.setRenderer(new ClassCellRenderer(myClassCombo.getRenderer()));
    gbc.gridwidth = 2;
    final JLabel classComboLabel = new JLabel();
    panel.add(classComboLabel, gbc);
    gbc.gridy++;
    panel.add(myClassCombo, gbc);
    classComboLabel.setText(RefactoringBundle.message("replace.inheritance.from"));

    myClassComboItemListener = new MyClassComboItemListener();
    myClassCombo.addItemListener(myClassComboItemListener);

    gbc.gridy++;
    gbc.gridwidth = 1;
    gbc.insets = JBUI.insets(4, 0);
    final JLabel fieldNameLabel = new JLabel();
    panel.add(fieldNameLabel, gbc);

    myFieldNameField = new NameSuggestionsField(myProject);
    gbc.gridx++;
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    gbc.insets = JBUI.insets(4, 0, 4, 8);
    gbc.weightx = 1.0;
    panel.add(myFieldNameField.getComponent(), gbc);
    fieldNameLabel.setText(RefactoringBundle.message("field.name"));

    //    if(InheritanceToDelegationUtil.isInnerClassNeeded(myClass, mySuperClass)) {
    gbc.gridx = 0;
    gbc.gridy++;
    gbc.gridwidth = 1;
    gbc.insets = JBUI.insets(4, 0);
    gbc.weightx = 0.0;
    final JLabel innerClassNameLabel = new JLabel();
    panel.add(innerClassNameLabel, gbc);

    /*String[] suggestions = new String[mySuperClasses.length];
    for (int i = 0; i < suggestions.length; i++) {
      suggestions[i] = "My" + mySuperClasses[i].getName();
    }*/
    myInnerClassNameField = new NameSuggestionsField(myProject);
    gbc.gridx++;
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    gbc.insets = JBUI.insets(4, 4, 4, 8);
    gbc.weightx = 1.0;
    panel.add(myInnerClassNameField.getComponent(), gbc);
    innerClassNameLabel.setText(RefactoringBundle.message("inner.class.name"));

    boolean innerClassNeeded = false;
    for (PsiClass superClass : mySuperClasses) {
      innerClassNeeded |= InheritanceToDelegationUtil.isInnerClassNeeded(myClass, superClass);
    }
    myInnerClassNameField.setVisible(innerClassNeeded);
    innerClassNameLabel.setVisible(innerClassNeeded);
    
    return panel;
  }


  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new GridBagLayout());
    GridBagConstraints gbc = new GridBagConstraints();

    gbc.fill = GridBagConstraints.BOTH;
    gbc.anchor = GridBagConstraints.WEST;
    gbc.gridy = 0;
    gbc.gridx = 0;
    gbc.weightx = 1.0;

    gbc.weighty = 1.0;
    gbc.gridwidth = 1;
    gbc.insets = JBUI.insets(4, 0, 4, 4);

    myMemberSelectionPanel = new MemberSelectionPanel(RefactoringBundle.message("delegate.members"), Collections.<MemberInfo>emptyList(), null);
    panel.add(myMemberSelectionPanel, gbc);
    MyMemberInfoModel memberInfoModel = new InheritanceToDelegationDialog.MyMemberInfoModel();
    myMemberSelectionPanel.getTable().setMemberInfoModel(memberInfoModel);
    myMemberSelectionPanel.getTable().addMemberInfoChangeListener(memberInfoModel);


    gbc.gridy++;
    gbc.insets = JBUI.insets(4, 8, 0, 8);
    gbc.weighty = 0.0;
    myCbGenerateGetter = new JCheckBox(RefactoringBundle.message("generate.getter.for.delegated.component"));
    myCbGenerateGetter.setFocusable(false);
    panel.add(myCbGenerateGetter, gbc);
    myCbGenerateGetter.setSelected(JavaRefactoringSettings.getInstance().INHERITANCE_TO_DELEGATION_DELEGATE_OTHER);
    updateTargetClass();

    return panel;
  }

  private void updateTargetClass() {
    final PsiClass targetClass = getSelectedTargetClass();
    PsiManager psiManager = myClass.getManager();
    PsiType superType = JavaPsiFacade.getInstance(psiManager.getProject()).getElementFactory().createType(targetClass);
    SuggestedNameInfo suggestedNameInfo =
      JavaCodeStyleManager.getInstance(psiManager.getProject()).suggestVariableName(VariableKind.FIELD, null, null, superType);
    myFieldNameField.setSuggestions(suggestedNameInfo.names);
    myInnerClassNameField.getComponent().setEnabled(InheritanceToDelegationUtil.isInnerClassNeeded(myClass, targetClass));
    @NonNls final String suggestion = "My" + targetClass.getName();
    myInnerClassNameField.setSuggestions(new String[]{suggestion});

    myDataChangedListener = new NameSuggestionsField.DataChanged() {
      public void dataChanged() {
        validateButtons();
      }
    };
    myInnerClassNameField.addDataChangedListener(myDataChangedListener);
    myFieldNameField.addDataChangedListener(myDataChangedListener);

    myMemberSelectionPanel.getTable().setMemberInfos(myBasesToMemberInfos.get(targetClass));
    myMemberSelectionPanel.getTable().fireExternalDataChange();
  }

  private class MyMemberInfoModel implements MemberInfoModel<PsiMember, MemberInfo> {
    final HashMap<PsiClass,InterfaceMemberDependencyGraph<PsiMember, MemberInfo>> myGraphs;

    public MyMemberInfoModel() {
      myGraphs = new HashMap<>();
      for (PsiClass superClass : mySuperClasses) {
        myGraphs.put(superClass, new InterfaceMemberDependencyGraph<>(superClass));
      }
    }

    public boolean isMemberEnabled(MemberInfo memberInfo) {
      if (getGraph().getDependent().contains(memberInfo.getMember())) {
        return false;
      }
      else {
        return true;
      }
    }

    public boolean isCheckedWhenDisabled(MemberInfo member) {
      return true;
    }

    public boolean isAbstractEnabled(MemberInfo member) {
      return false;
    }

    public boolean isAbstractWhenDisabled(MemberInfo member) {
      return false;
    }

    public Boolean isFixedAbstract(MemberInfo member) {
      return null;
    }

    public int checkForProblems(@NotNull MemberInfo member) {
      return OK;
    }

    public String getTooltipText(MemberInfo member) {
      return null;
    }

    public void memberInfoChanged(MemberInfoChange<PsiMember, MemberInfo> event) {
      final Collection<MemberInfo> changedMembers = event.getChangedMembers();

      for (MemberInfo changedMember : changedMembers) {
        getGraph().memberChanged(changedMember);
      }
    }

    private InterfaceMemberDependencyGraph<PsiMember, MemberInfo> getGraph() {
      return myGraphs.get(getSelectedTargetClass());
    }
  }

  private class MyClassComboItemListener implements ItemListener {
    public void itemStateChanged(ItemEvent e) {
      if (e.getStateChange() == ItemEvent.SELECTED) {
        updateTargetClass();
      }
    }
  }
}
