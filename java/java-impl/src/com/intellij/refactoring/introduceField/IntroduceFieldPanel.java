/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.refactoring.introduceField;

import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.ui.TypeSelectorManager;
import com.intellij.ui.NonFocusableCheckBox;
import com.intellij.ui.StateRestoringCheckBox;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

/**
 * User: anna
 * Date: 3/15/11
 */
public class IntroduceFieldPanel {
  static boolean ourLastCbFinalState = false;

  private JCheckBox myCbReplaceAll;
  private StateRestoringCheckBox myCbDeleteVariable;
  private StateRestoringCheckBox myCbFinal;
  private boolean myIsInvokedOnDeclaration;
  private int myOccurrencesCount;
  private PsiLocalVariable myLocalVariable;

  public IntroduceFieldPanel(boolean isInvokedOnDeclaration, int occurrencesCount, PsiLocalVariable localVariable) {
    myIsInvokedOnDeclaration = isInvokedOnDeclaration;
    myOccurrencesCount = occurrencesCount;
    myLocalVariable = localVariable;
  }

  public void initializeControls(PsiExpression initializerExpression) {
    if (initializerExpression != null && !InitializerPlaceChooser.setEnabledInitializationPlaces(initializerExpression, initializerExpression)) {
      myCbFinal.setEnabled(false);
    }
    myCbFinal.setSelected(myCbFinal.isEnabled() && ourLastCbFinalState);
  }

  public boolean isReplaceAllOccurrences() {
    if (myIsInvokedOnDeclaration) return true;
    if (myOccurrencesCount <= 1) return false;
    return myCbReplaceAll.isSelected();
  }

  public boolean isDeclareFinal() {
    return myCbFinal.isSelected();
  }

  public boolean isDeleteVariable() {
    if (myIsInvokedOnDeclaration) return true;
    if (myCbDeleteVariable == null) return false;
    return myCbDeleteVariable.isSelected();
  }

  void appendFinalCb(JPanel mainPanel, GridBagConstraints gbConstraints, ItemListener itemListener) {
    myCbFinal = new StateRestoringCheckBox();
    myCbFinal.setText(RefactoringBundle.message("declare.final"));
    myCbFinal.addItemListener(itemListener);
    gbConstraints.gridy++;
    mainPanel.add(myCbFinal, gbConstraints);
  }

  void appendOccurrencesCb(JPanel panel, GridBagConstraints gbConstraints, ItemListener itemListener) {
    if (myOccurrencesCount > 1) {
      myCbReplaceAll = new NonFocusableCheckBox();
      myCbReplaceAll.setText(RefactoringBundle.message("replace.all.occurrences.of.expression.0.occurrences", myOccurrencesCount));
      gbConstraints.gridy++;
      panel.add(myCbReplaceAll, gbConstraints);
      myCbReplaceAll.addItemListener(itemListener);
      if (myIsInvokedOnDeclaration) {
        myCbReplaceAll.setEnabled(false);
        myCbReplaceAll.setSelected(true);
      }
    }
  }

  void appendDeleteVariableDeclarationCb(JPanel panel, GridBagConstraints gbConstraints) {
    if (myLocalVariable != null) {
      gbConstraints.gridy++;
      if (myCbReplaceAll != null) {
        gbConstraints.insets = new Insets(0, 8, 0, 0);
      }
      myCbDeleteVariable = new StateRestoringCheckBox();
      myCbDeleteVariable.setText(RefactoringBundle.message("delete.variable.declaration"));
      panel.add(myCbDeleteVariable, gbConstraints);
      if (myIsInvokedOnDeclaration) {
        myCbDeleteVariable.setEnabled(false);
        myCbDeleteVariable.setSelected(true);
      } else if (myCbReplaceAll != null) {
        updateCbDeleteVariable();
        myCbReplaceAll.addItemListener(
                new ItemListener() {
                  public void itemStateChanged(ItemEvent e) {
                    updateCbDeleteVariable();
                  }
                }
        );
      }
      gbConstraints.insets = new Insets(0, 0, 0, 0);
    }
  }

  private void updateCbDeleteVariable() {
    if (!myCbReplaceAll.isSelected()) {
      myCbDeleteVariable.makeUnselectable(false);
    } else {
      myCbDeleteVariable.makeSelectable();
    }
  }

  void updateCbFinal(boolean allowFinal) {
    if (!allowFinal) {
      myCbFinal.makeUnselectable(false);
    } else {
      myCbFinal.makeSelectable();
    }
  }

  public boolean isFinal() {
    return myCbFinal.isSelected();
  }

  void updateTypeSelector(TypeSelectorManager typeSelectorManager) {
    if (myCbReplaceAll != null) {
      typeSelectorManager.setAllOccurences(myCbReplaceAll.isSelected());
    } else {
      typeSelectorManager.setAllOccurences(false);
    }
  }

  public boolean hasOccurrences() {
    return myCbReplaceAll != null;
  }
}
