// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.inheritanceToDelegation;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.classMembers.MemberInfoChange;
import com.intellij.refactoring.classMembers.MemberInfoModel;
import com.intellij.refactoring.ui.ClassCellRenderer;
import com.intellij.refactoring.ui.MemberSelectionPanel;
import com.intellij.refactoring.ui.NameSuggestionsField;
import com.intellij.refactoring.ui.RefactoringDialog;
import com.intellij.refactoring.util.classMembers.InterfaceMemberDependencyGraph;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.util.ui.JBInsets;
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
  private MyClassComboItemListener myClassComboItemListener;
  private NameSuggestionsField.DataChanged myDataChangedListener;

  public InheritanceToDelegationDialog(Project project,
                                       PsiClass aClass,
                                       PsiClass[] superClasses,
                                       HashMap<PsiClass,Collection<MemberInfo>> basesToMemberInfos) {
    super(project, true);
    myClass = aClass;
    mySuperClasses = superClasses;
    myBasesToMemberInfos = basesToMemberInfos;

    setTitle(InheritanceToDelegationHandler.getRefactoringName());
    init();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myFieldNameField;
  }

  @Override
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
      throw new ConfigurationException(JavaRefactoringBundle.message("replace.inheritance.with.delegation.invalid.field", fieldName));
    }
    if (myInnerClassNameField != null) {
      final String className = myInnerClassNameField.getEnteredName();
      if (!helper.isIdentifier(className)) {
        throw new ConfigurationException(JavaRefactoringBundle.message("replace.inheritance.with.delegation.invalid.inner.class",
                                                                       fieldName));
      }
    }
  }

  public Collection<MemberInfo> getSelectedMemberInfos() {
    return myMemberSelectionPanel.getTable().getSelectedMemberInfos();
  }

  public PsiClass getSelectedTargetClass() {
    return (PsiClass)myClassCombo.getSelectedItem();
  }

  @Override
  protected String getHelpId() {
    return HelpID.INHERITANCE_TO_DELEGATION;
  }

  @Override
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
                                                           implementedInterfaces.toArray(PsiClass.EMPTY_ARRAY),
                                                           delegatedMethods.toArray(PsiMethod.EMPTY_ARRAY),
                                                           isGenerateGetter(), isGenerateGetter()));
  }

  @Override
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
    classComboLabel.setText(JavaRefactoringBundle.message("replace.inheritance.from"));

    myClassComboItemListener = new MyClassComboItemListener();
    myClassCombo.addItemListener(myClassComboItemListener);

    gbc.gridy++;
    gbc.gridwidth = 1;
    gbc.insets = JBInsets.create(4, 0);
    final JLabel fieldNameLabel = new JLabel();
    panel.add(fieldNameLabel, gbc);

    myFieldNameField = new NameSuggestionsField(myProject);
    gbc.gridx++;
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    gbc.insets = JBUI.insets(4, 0, 4, 8);
    gbc.weightx = 1.0;
    panel.add(myFieldNameField.getComponent(), gbc);
    fieldNameLabel.setText(JavaRefactoringBundle.message("field.name"));

    //    if(InheritanceToDelegationUtil.isInnerClassNeeded(myClass, mySuperClass)) {
    gbc.gridx = 0;
    gbc.gridy++;
    gbc.gridwidth = 1;
    gbc.insets = JBInsets.create(4, 0);
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
    innerClassNameLabel.setText(JavaRefactoringBundle.message("inner.class.name"));

    boolean innerClassNeeded = false;
    for (PsiClass superClass : mySuperClasses) {
      innerClassNeeded |= InheritanceToDelegationUtil.isInnerClassNeeded(myClass, superClass);
    }
    myInnerClassNameField.setVisible(innerClassNeeded);
    innerClassNameLabel.setVisible(innerClassNeeded);

    return panel;
  }


  @Override
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

    String delegatePanelTitle = "replace.inheritance.with.delegation.delegate.members.title";
    myMemberSelectionPanel = new MemberSelectionPanel(JavaRefactoringBundle.message(delegatePanelTitle), Collections.emptyList(), null);
    panel.add(myMemberSelectionPanel, gbc);
    MyMemberInfoModel memberInfoModel = new InheritanceToDelegationDialog.MyMemberInfoModel();
    myMemberSelectionPanel.getTable().setMemberInfoModel(memberInfoModel);
    myMemberSelectionPanel.getTable().addMemberInfoChangeListener(memberInfoModel);


    gbc.gridy++;
    gbc.insets = JBUI.insets(4, 8, 0, 8);
    gbc.weighty = 0.0;
    myCbGenerateGetter = new JCheckBox(JavaRefactoringBundle.message("generate.getter.for.delegated.component"));
    myCbGenerateGetter.setFocusable(false);
    panel.add(myCbGenerateGetter, gbc);
    myCbGenerateGetter.setSelected(JavaRefactoringSettings.getInstance().INHERITANCE_TO_DELEGATION_DELEGATE_OTHER);
    updateTargetClass();

    return panel;
  }

  private void updateTargetClass() {
    final PsiClass targetClass = getSelectedTargetClass();
    PsiManager psiManager = myClass.getManager();
    PsiType superType = JavaPsiFacade.getElementFactory(psiManager.getProject()).createType(targetClass);
    SuggestedNameInfo suggestedNameInfo =
      JavaCodeStyleManager.getInstance(psiManager.getProject()).suggestVariableName(VariableKind.FIELD, null, null, superType);
    myFieldNameField.setSuggestions(suggestedNameInfo.names);
    myInnerClassNameField.getComponent().setEnabled(InheritanceToDelegationUtil.isInnerClassNeeded(myClass, targetClass));
    @NonNls final String suggestion = "My" + targetClass.getName();
    myInnerClassNameField.setSuggestions(new String[]{suggestion});

    myDataChangedListener = () -> validateButtons();
    myInnerClassNameField.addDataChangedListener(myDataChangedListener);
    myFieldNameField.addDataChangedListener(myDataChangedListener);

    myMemberSelectionPanel.getTable().setMemberInfos(myBasesToMemberInfos.get(targetClass));
    myMemberSelectionPanel.getTable().fireExternalDataChange();
  }

  private class MyMemberInfoModel implements MemberInfoModel<PsiMember, MemberInfo> {
    final HashMap<PsiClass,InterfaceMemberDependencyGraph<PsiMember, MemberInfo>> myGraphs;

    MyMemberInfoModel() {
      myGraphs = new HashMap<>();
      for (PsiClass superClass : mySuperClasses) {
        myGraphs.put(superClass, new InterfaceMemberDependencyGraph<>(superClass));
      }
    }

    @Override
    public boolean isMemberEnabled(MemberInfo memberInfo) {
      if (getGraph().getDependent().contains(memberInfo.getMember())) {
        return false;
      }
      else {
        return true;
      }
    }

    @Override
    public boolean isCheckedWhenDisabled(MemberInfo member) {
      return true;
    }

    @Override
    public boolean isAbstractEnabled(MemberInfo member) {
      return false;
    }

    @Override
    public boolean isAbstractWhenDisabled(MemberInfo member) {
      return false;
    }

    @Override
    public Boolean isFixedAbstract(MemberInfo member) {
      return null;
    }

    @Override
    public int checkForProblems(@NotNull MemberInfo member) {
      return OK;
    }

    @Override
    public String getTooltipText(MemberInfo member) {
      return null;
    }

    @Override
    public void memberInfoChanged(@NotNull MemberInfoChange<PsiMember, MemberInfo> event) {
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
    @Override
    public void itemStateChanged(ItemEvent e) {
      if (e.getStateChange() == ItemEvent.SELECTED) {
        updateTargetClass();
      }
    }
  }
}
