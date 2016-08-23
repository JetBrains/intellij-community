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
package com.intellij.refactoring.move.moveInstanceMethod;

import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.psi.*;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.move.MoveInstanceMembersUtil;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.TitledSeparator;
import com.intellij.util.containers.HashMap;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * @author ven
 */
public class MoveInstanceMethodDialog extends MoveInstanceMethodDialogBase {
  @NonNls private static final String KEY = "#com.intellij.refactoring.move.moveInstanceMethod.MoveInstanceMethodDialog";

  //Map from classes referenced by 'this' to sets of referenced members
  private Map<PsiClass, Set<PsiMember>> myThisClassesMap;

  private Map<PsiClass, EditorTextField> myOldClassParameterNameFields;

  public MoveInstanceMethodDialog(final PsiMethod method,
                                  final PsiVariable[] variables) {
    super(method, variables, MoveInstanceMethodHandler.REFACTORING_NAME);
    init();
  }

  protected String getDimensionServiceKey() {
    return KEY;
  }

  protected JComponent createCenterPanel() {
    JPanel mainPanel = new JPanel(new GridBagLayout());
    final TitledSeparator separator = new TitledSeparator();
    mainPanel.add(separator, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 2, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL,
                                                    JBUI.emptyInsets(), 0, 0));

    myList = createTargetVariableChooser();
    myList.addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        validateTextFields(e.getFirstIndex());
      }
    });

    separator.setText(RefactoringBundle.message("moveInstanceMethod.select.an.instance.parameter"));

    final JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myList);
    mainPanel.add(scrollPane, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 1.0, GridBagConstraints.WEST, GridBagConstraints.BOTH,
                                                     JBUI.emptyInsets(), 0, 0));

    myVisibilityPanel = createVisibilityPanel();
    mainPanel.add(myVisibilityPanel, new GridBagConstraints(1, GridBagConstraints.RELATIVE, 1, 1, 0.0, 1.0, GridBagConstraints.WEST, GridBagConstraints.VERTICAL,
                                                            JBUI.emptyInsets(), 0, 0));

    final JPanel parametersPanel = createParametersPanel();
    if (parametersPanel != null) {
      mainPanel.add(parametersPanel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL,
                                                            JBUI.emptyInsets(), 0, 0));
    }

    mainPanel.add(initOpenInEditorCb(), new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1, 0, GridBagConstraints.EAST, GridBagConstraints.HORIZONTAL,
                                                               JBUI.emptyInsets(), 0, 0));

    separator.setLabelFor(myList);
    validateTextFields(myList.getSelectedIndex());

    updateOnChanged(myList);
    return mainPanel;
  }

  private void validateTextFields(final int selectedIndex) {
    for (EditorTextField textField : myOldClassParameterNameFields.values()) {
      textField.setEnabled(true);
    }

    final PsiVariable variable = myVariables[selectedIndex];
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
      final String text = RefactoringBundle.message("move.method.this.parameter.label", aClass.getName());
      panel.add(new TitledSeparator(text, null));

      String suggestedName = MoveInstanceMethodHandler.suggestParameterNameForThisClass(aClass);
      final EditorTextField field = new EditorTextField(suggestedName, getProject(), StdFileTypes.JAVA);
      field.setMinimumSize(new Dimension(field.getPreferredSize()));
      myOldClassParameterNameFields.put(aClass, field);
      panel.add(field);
    }
    panel.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));
    return panel;
  }

  protected void doAction() {
    Map<PsiClass, String> parameterNames = new LinkedHashMap<>();
    for (final PsiClass aClass : myThisClassesMap.keySet()) {
      EditorTextField field = myOldClassParameterNameFields.get(aClass);
      if (field.isEnabled()) {
        String parameterName = field.getText().trim();
        if (!PsiNameHelper.getInstance(myMethod.getProject()).isIdentifier(parameterName)) {
          Messages
            .showErrorDialog(getProject(), RefactoringBundle.message("move.method.enter.a.valid.name.for.parameter"), myRefactoringName);
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
    saveOpenInEditorOption();
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

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(HelpID.MOVE_INSTANCE_METHOD);
  }

  @Override
  protected String getMovePropertySuffix() {
    return "Instance";
  }

  @Override
  protected String getCbTitle() {
    return "Open moved method in editor";
  }
}
