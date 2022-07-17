// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.convertToInstanceMethod;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.move.moveInstanceMethod.MoveInstanceMethodDialogBase;
import com.intellij.ui.DoubleClickListener;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;

/**
 * @author ven
 */
public class ConvertToInstanceMethodDialog  extends MoveInstanceMethodDialogBase {
  private static final Logger LOG = Logger.getInstance(ConvertToInstanceMethodDialog.class);

  public ConvertToInstanceMethodDialog(final PsiMethod method, final Object[] variables) {
    super(method, variables, ConvertToInstanceMethodHandler.getRefactoringName(), false);
    init();
  }

  @Override
  protected void doAction() {
    final Object targetVariable = myList.getSelectedValue();
    LOG.assertTrue(targetVariable != null);
    final ConvertToInstanceMethodProcessor processor = new ConvertToInstanceMethodProcessor(myMethod.getProject(),
                                                                                            myMethod, targetVariable instanceof PsiParameter ? (PsiParameter)targetVariable : null,
                                                                                            myVisibilityPanel.getVisibility());
    if (!verifyTargetClass(processor.getTargetClass())) return;
    invokeRefactoring(processor);
  }

  @Override
  protected String getHelpId() {
    return HelpID.CONVERT_TO_INSTANCE_METHOD;
  }

  @Override
  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new BorderLayout(UIUtil.DEFAULT_HGAP, UIUtil.DEFAULT_VGAP));
    final JLabel label = new JLabel(RefactoringBundle.message("moveInstanceMethod.select.an.instance.parameter"));
    panel.add(label, BorderLayout.NORTH);
    panel.add(createListAndVisibilityPanels(), BorderLayout.CENTER);
    return panel;
  }

  @Override
  protected JList createTargetVariableChooser() {
    final JList variableChooser = super.createTargetVariableChooser();
    new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(@NotNull MouseEvent e) {
        Point point = e.getPoint();
        int index = variableChooser.locationToIndex(point);
        if (index == -1) return false;
        if (!variableChooser.getCellBounds(index, index).contains(point)) return false;
        doRefactorAction();
        return true;
      }
    }.installOn(variableChooser);
    return variableChooser;
  }
}