// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.move.moveInstanceMethod;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.psi.*;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.move.MoveInstanceMembersUtil;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.TitledSeparator;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * @author ven
 */
public class MoveInstanceMethodDialog extends MoveInstanceMethodDialogBase {
  private static final String KEY = "#com.intellij.refactoring.move.moveInstanceMethod.MoveInstanceMethodDialog";

  //Map from classes referenced by 'this' to sets of referenced members
  private Map<PsiClass, Set<PsiMember>> myThisClassesMap;

  private Map<PsiClass, EditorTextField> myOldClassParameterNameFields;

  public MoveInstanceMethodDialog(final PsiMethod method,
                                  final PsiVariable[] variables) {
    super(method, variables, MoveInstanceMethodHandler.getRefactoringName(), true);
    init();
  }

  @Override
  protected String getDimensionServiceKey() {
    return KEY;
  }

  @Override
  protected JComponent createCenterPanel() {
    JPanel mainPanel = new JPanel(new GridBagLayout());
    final TitledSeparator separator = new TitledSeparator();
    mainPanel.add(separator, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 2, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL,
                                                    JBInsets.emptyInsets(), 0, 0));

    myList = createTargetVariableChooser();
    myList.addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        validateTextFields(e.getFirstIndex());
      }
    });

    separator.setText(RefactoringBundle.message("moveInstanceMethod.select.an.instance.parameter"));

    final JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myList);
    mainPanel.add(scrollPane, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 1.0, GridBagConstraints.WEST, GridBagConstraints.BOTH,
                                                     JBInsets.emptyInsets(), 0, 0));

    myVisibilityPanel = createVisibilityPanel();
    mainPanel.add(myVisibilityPanel, new GridBagConstraints(1, GridBagConstraints.RELATIVE, 1, 1, 0.0, 1.0, GridBagConstraints.WEST, GridBagConstraints.VERTICAL,
                                                            JBInsets.emptyInsets(), 0, 0));

    final JPanel parametersPanel = createParametersPanel();
    if (parametersPanel != null) {
      mainPanel.add(parametersPanel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL,
                                                            JBInsets.emptyInsets(), 0, 0));
    }

    separator.setLabelFor(myList);
    validateTextFields(myList.getSelectedIndex());

    updateOnChanged(myList);
    return mainPanel;
  }

  private void validateTextFields(final int selectedIndex) {
    for (EditorTextField textField : myOldClassParameterNameFields.values()) {
      textField.setEnabled(true);
    }

    final Object variable = myVariables[selectedIndex];
    if (variable instanceof PsiField) {
      final PsiField field = (PsiField)variable;
      final PsiClass hisClass = field.getContainingClass();
      final Set<PsiMember> members = myThisClassesMap.get(hisClass);
      if (members != null && members.size() == 1 && members.contains(field)) {  //Just the field is referenced
        myOldClassParameterNameFields.get(hisClass).setEnabled(false);
      }
    }
  }

  @Nullable
  private JPanel createParametersPanel () {
    myThisClassesMap = MoveInstanceMembersUtil.getThisClassesToMembers(myMethod);
    myOldClassParameterNameFields = new HashMap<>();
    if (myThisClassesMap.size() == 0) return null;
    JPanel panel = new JPanel(new VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 0, true, true));
    for (PsiClass aClass : myThisClassesMap.keySet()) {
      final String text = JavaRefactoringBundle.message("move.method.this.parameter.label", ObjectUtils.notNull(aClass.getName(), ""));
      panel.add(new TitledSeparator(text, null));

      String suggestedName = MoveInstanceMethodHandler.suggestParameterNameForThisClass(aClass);
      final EditorTextField field = new EditorTextField(suggestedName, getProject(), JavaFileType.INSTANCE);
      field.setMinimumSize(new Dimension(field.getPreferredSize()));
      myOldClassParameterNameFields.put(aClass, field);
      panel.add(field);
    }
    panel.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));
    return panel;
  }

  @Override
  protected void doAction() {
    Map<PsiClass, String> parameterNames = new LinkedHashMap<>();
    for (final PsiClass aClass : myThisClassesMap.keySet()) {
      EditorTextField field = myOldClassParameterNameFields.get(aClass);
      if (field.isEnabled()) {
        String parameterName = field.getText().trim();
        if (!PsiNameHelper.getInstance(myMethod.getProject()).isIdentifier(parameterName)) {
          Messages
            .showErrorDialog(getProject(), JavaRefactoringBundle.message("move.method.enter.a.valid.name.for.parameter"), myRefactoringName);
          return;
        }
        parameterNames.put(aClass, parameterName);
      }
    }

    final PsiVariable targetVariable = (PsiVariable)myList.getSelectedValue();
    if (targetVariable == null) return;
    final MoveInstanceMethodProcessor processor = new MoveInstanceMethodProcessor(myMethod.getProject(),
                                                                                  myMethod, targetVariable,
                                                                                  myVisibilityPanel.getVisibility(),
                                                                                  isOpenInEditor(),
                                                                                  parameterNames);
    if (!verifyTargetClass(processor.getTargetClass())) return;
    invokeRefactoring(processor);
  }

  @Override
  protected void updateOnChanged(JList list) {
    super.updateOnChanged(list);
    final PsiVariable selectedValue = (PsiVariable)list.getSelectedValue();
    if (selectedValue != null) {
      final PsiClassType psiType = (PsiClassType)selectedValue.getType();
      final PsiClass targetClass = psiType.resolve();
      UIUtil.setEnabled(myVisibilityPanel, targetClass != null && !targetClass.isInterface(), true);
    }
  }

  @Override
  protected String getHelpId() {
    return HelpID.MOVE_INSTANCE_METHOD;
  }

  @Override
  protected @NotNull String getRefactoringId() {
    return "MoveInstance";
  }
}