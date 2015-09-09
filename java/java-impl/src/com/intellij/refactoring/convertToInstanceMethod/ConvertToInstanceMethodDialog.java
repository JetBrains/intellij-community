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
package com.intellij.refactoring.convertToInstanceMethod;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.help.HelpManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiVariable;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.move.moveInstanceMethod.MoveInstanceMethodDialogBase;
import com.intellij.ui.DoubleClickListener;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;

/**
 * @author ven
 */
public class ConvertToInstanceMethodDialog  extends MoveInstanceMethodDialogBase {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.convertToInstanceMethod.ConvertToInstanceMethodDialog");
  public ConvertToInstanceMethodDialog(final PsiMethod method, final PsiParameter[] variables) {
    super(method, variables, ConvertToInstanceMethodHandler.REFACTORING_NAME);
    init();
  }

  protected void doAction() {
    final PsiVariable targetVariable = (PsiVariable)myList.getSelectedValue();
    LOG.assertTrue(targetVariable instanceof PsiParameter, targetVariable);
    final ConvertToInstanceMethodProcessor processor = new ConvertToInstanceMethodProcessor(myMethod.getProject(),
                                                                                            myMethod, (PsiParameter)targetVariable,
                                                                                            myVisibilityPanel.getVisibility());
    if (!verifyTargetClass(processor.getTargetClass())) return;
    invokeRefactoring(processor);
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(HelpID.CONVERT_TO_INSTANCE_METHOD);
  }

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
      protected boolean onDoubleClick(MouseEvent e) {
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

  @Override
  protected String getMovePropertySuffix() {
    return null;
  }

  @Override
  protected String getCbTitle() {
    return null;
  }
}
