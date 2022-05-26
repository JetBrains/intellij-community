// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.inline;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.psi.PsiCallExpression;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.ui.RefactoringDialog;

import javax.swing.*;
import java.awt.*;


public class InlineParameterDialog extends RefactoringDialog {
  private JCheckBox myCreateLocalCheckbox;
  private final PsiCallExpression myMethodCall;
  private final PsiMethod myMethod;
  private final PsiParameter myParameter;
  private final PsiExpression myInitializer;

  public InlineParameterDialog(PsiCallExpression methodCall, PsiMethod method, PsiParameter psiParameter, PsiExpression initializer,
                               boolean createLocal) {
    super(method.getProject(), true);
    myMethodCall = methodCall;
    myMethod = method;
    myParameter = psiParameter;
    myInitializer = initializer;
    init();
    myCreateLocalCheckbox.setSelected(createLocal);
    setTitle(InlineParameterHandler.getRefactoringName());
  }

  @Override
  protected JComponent createNorthPanel() {
    final JPanel panel = new JPanel(new BorderLayout());
    String message = JavaRefactoringBundle.message("inline.parameter.confirmation", myParameter.getName(), myInitializer.getText());
    JLabel label = new JLabel(message, UIManager.getIcon("OptionPane.questionIcon"), SwingConstants.LEFT);
    panel.add(label, BorderLayout.NORTH);
    return panel;
  }

  @Override
  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new BorderLayout());
    myCreateLocalCheckbox = new JCheckBox(JavaRefactoringBundle.message("inline.parameter.replace.with.local.checkbox"));
    panel.add(myCreateLocalCheckbox, BorderLayout.SOUTH);
    return panel;
  }

  @Override
  protected String getHelpId() {
    return HelpID.INLINE_VARIABLE;
  }

  @Override
  protected void doAction() {
    invokeRefactoring(new InlineParameterExpressionProcessor(myMethodCall, myMethod, myParameter, myInitializer, myCreateLocalCheckbox.isSelected()));
  }
}
