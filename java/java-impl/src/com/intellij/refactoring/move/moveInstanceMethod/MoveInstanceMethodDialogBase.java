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

import com.intellij.lang.findUsages.DescriptiveNameUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.move.MoveDialogBase;
import com.intellij.refactoring.ui.JavaVisibilityPanel;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBList;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;

/**
 * @author dsl
 */
public abstract class MoveInstanceMethodDialogBase extends MoveDialogBase {
  protected final PsiMethod myMethod;
  protected final PsiVariable[] myVariables;

  public JComponent getPreferredFocusedComponent() {
    return myList;
  }

  protected JList myList;
  protected JavaVisibilityPanel myVisibilityPanel;
  protected final String myRefactoringName;

  public MoveInstanceMethodDialogBase(PsiMethod method, PsiVariable[] variables, String refactoringName) {
    super(method.getProject(), true);
    myMethod = method;
    myVariables = variables;
    myRefactoringName = refactoringName;
    setTitle(myRefactoringName);
  }

  protected JPanel createListAndVisibilityPanels() {
    myList = createTargetVariableChooser();
    final JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myList);
    final JPanel hBox = new JPanel(new GridBagLayout());
    final GridBagConstraints gbConstraints = new GridBagConstraints();

    gbConstraints.fill = GridBagConstraints.BOTH;
    gbConstraints.weightx = 1;
    gbConstraints.weighty = 1;
    gbConstraints.gridheight = 1;
    gbConstraints.gridx = 0;
    gbConstraints.gridy = 0;
    gbConstraints.insets = JBUI.emptyInsets();
    hBox.add(scrollPane, gbConstraints);
    hBox.add(Box.createHorizontalStrut(4));
    gbConstraints.weightx = 0;
    gbConstraints.fill = GridBagConstraints.NONE;
    gbConstraints.anchor = GridBagConstraints.NORTH;
    gbConstraints.gridx++;
    myVisibilityPanel = createVisibilityPanel();
    hBox.add (myVisibilityPanel, gbConstraints);
    return hBox;
  }

  protected JList createTargetVariableChooser() {
    final JList list = new JBList(new MyListModel());
    list.setCellRenderer(new MyListCellRenderer());
    list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    list.setSelectedIndex(0);
    list.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        updateOnChanged(list);
      }
    });
    return list;
  }

  protected void updateOnChanged(JList list) {
    getOKAction().setEnabled(!list.getSelectionModel().isSelectionEmpty());
  }

  protected static JavaVisibilityPanel createVisibilityPanel() {
    final JavaVisibilityPanel visibilityPanel = new JavaVisibilityPanel(false, true);
    visibilityPanel.setVisibility(null);
    return visibilityPanel;
  }

  protected boolean verifyTargetClass (PsiClass targetClass) {
    if (targetClass.isInterface() && !PsiUtil.isLanguageLevel8OrHigher(targetClass)) {
      final Project project = getProject();
      if (ClassInheritorsSearch.search(targetClass, false).findFirst() == null) {
        final String message = RefactoringBundle.message("0.is.an.interface.that.has.no.implementing.classes", DescriptiveNameUtil
          .getDescriptiveName(targetClass));

        Messages.showErrorDialog(project, message, myRefactoringName);
        return false;
      }

      final String message = RefactoringBundle.message("0.is.an.interface.method.implementation.will.be.added.to.all.directly.implementing.classes",
                                                       DescriptiveNameUtil.getDescriptiveName(targetClass));

      final int result = Messages.showYesNoDialog(project, message, myRefactoringName,
                                                  Messages.getQuestionIcon());
      if (result != Messages.YES) return false;
    }

    return true;
  }

  private class MyListModel extends AbstractListModel {
    public int getSize() {
      return myVariables.length;
    }

    public Object getElementAt(int index) {
      return myVariables[index];
    }
  }

  private static class MyListCellRenderer extends DefaultListCellRenderer {
    public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
      super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
      final PsiVariable psiVariable = (PsiVariable)value;
      final String text = PsiFormatUtil.formatVariable(psiVariable,
                                                       PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_TYPE,
                                                       PsiSubstitutor.EMPTY);
      setIcon(psiVariable.getIcon(0));
      setText(text);
      return this;
    }
  }
}
