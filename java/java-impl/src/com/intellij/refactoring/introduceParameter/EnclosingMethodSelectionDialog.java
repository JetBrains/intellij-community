// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.refactoring.introduceParameter;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.ui.MethodCellRenderer;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBList;
import com.intellij.util.ui.JBInsets;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class EnclosingMethodSelectionDialog extends DialogWrapper {
  private final List<PsiMethod> myEnclosingMethods;

  private JList<PsiMethod> myEnclosingMethodsList;
  private final JCheckBox myCbReplaceInstanceOf = new JCheckBox(JavaRefactoringBundle.message("use.interface.superclass.in.instanceof"));

  EnclosingMethodSelectionDialog(Project project, List<PsiMethod> enclosingMethods) {
    super(project, true);

    myEnclosingMethods = enclosingMethods;

    setTitle(getRefactoringName());
    init();
  }

  public PsiMethod getSelectedMethod() {
    if(myEnclosingMethodsList != null) {
      return myEnclosingMethodsList.getSelectedValue();
    }
    else {
      return null;
    }
  }

  @Override
  protected Action @NotNull [] createActions() {
    return new Action[]{getOKAction(), getCancelAction()/*, getHelpAction()*/};
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myEnclosingMethodsList;
  }

  @Override
  protected JComponent createNorthPanel() {
    JPanel panel = new JPanel();

    panel.setLayout(new GridBagLayout());
    GridBagConstraints gbConstraints = new GridBagConstraints();

    gbConstraints.insets = JBInsets.create(4, 8);
    gbConstraints.weighty = 0;
    gbConstraints.weightx = 1;
    gbConstraints.gridy = 0;
    gbConstraints.gridwidth = GridBagConstraints.REMAINDER;
    gbConstraints.gridheight = 1;
    gbConstraints.fill = GridBagConstraints.BOTH;
    gbConstraints.anchor = GridBagConstraints.WEST;
    panel.add(new JLabel(JavaRefactoringBundle.message("introduce.parameter.to.method")), gbConstraints);

    gbConstraints.weighty = 1;
    myEnclosingMethodsList = new JBList<>(myEnclosingMethods);
    myEnclosingMethodsList.setCellRenderer(new MethodCellRenderer());
    myEnclosingMethodsList.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    int indexToSelect = 0;
    myEnclosingMethodsList.setSelectedIndex(indexToSelect);
    gbConstraints.gridy++;
    panel.add(ScrollPaneFactory.createScrollPane(myEnclosingMethodsList), gbConstraints);

    return panel;
  }

  @Override
  protected String getDimensionServiceKey() {
    return "#com.intellij.refactoring.introduceParameter.EnclosingMethodSelectonDialog";
  }

  @Override
  protected void doOKAction() {
    if (!isOKActionEnabled())
      return;

    super.doOKAction();
  }

  @Override
  protected JComponent createCenterPanel() {
    return null;
  }

  private static @NlsContexts.DialogTitle String getRefactoringName() {
    return RefactoringBundle.message("introduce.parameter.title");
  }
}
