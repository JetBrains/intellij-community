/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.refactoring.introduceParameter;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.IntroduceParameterRefactoring;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.introduce.inplace.KeyboardComboSwitcher;
import com.intellij.refactoring.ui.TypeSelectorManager;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import gnu.trove.TIntArrayList;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

public abstract class InplaceIntroduceParameterUI extends IntroduceParameterSettingsUI {
  private JComboBox myReplaceFieldsCb;
  private boolean myHasWriteAccess;
  private final Project myProject;
  private final TypeSelectorManager myTypeSelectorManager;
  private final PsiExpression[] myOccurrences;

  public InplaceIntroduceParameterUI(Project project,
                                     PsiLocalVariable onLocalVariable,
                                     PsiExpression onExpression,
                                     PsiMethod methodToReplaceIn,
                                     TIntArrayList parametersToRemove,
                                     TypeSelectorManager typeSelectorManager,
                                     PsiExpression[] occurrences) {
    super(onLocalVariable, onExpression, methodToReplaceIn, parametersToRemove);
    myProject = project;
    myTypeSelectorManager = typeSelectorManager;
    myOccurrences = occurrences;

    for (PsiExpression occurrence : myOccurrences) {
      if (PsiUtil.isAccessedForWriting(occurrence)) {
        myHasWriteAccess = true;
        break;
      }
    }
  }

  protected abstract PsiParameter getParameter();

  @Override
  protected JPanel createReplaceFieldsWithGettersPanel() {
    final LabeledComponent<JComboBox> component = new LabeledComponent<>();
    myReplaceFieldsCb = new JComboBox(new Integer[]{IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_ALL,
      IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE,
      IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE});
    myReplaceFieldsCb.setRenderer(new ListCellRendererWrapper<Integer>() {
      @Override
      public void customize(JList list, Integer value, int index, boolean selected, boolean hasFocus) {
        switch (value) {
          case IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE:
            setText(UIUtil.removeMnemonic(RefactoringBundle.message("do.not.replace")));
            break;
          case IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE:
            setText(UIUtil.removeMnemonic(RefactoringBundle.message("replace.fields.inaccessible.in.usage.context")));
            break;
          default:
            setText(UIUtil.removeMnemonic(RefactoringBundle.message("replace.all.fields")));
        }
      }
    });
    myReplaceFieldsCb.setSelectedItem(JavaRefactoringSettings.getInstance().INTRODUCE_PARAMETER_REPLACE_FIELDS_WITH_GETTERS);
    KeyboardComboSwitcher.setupActions(myReplaceFieldsCb, myProject);
    component.setComponent(myReplaceFieldsCb);
    component.setText(RefactoringBundle.message("replace.fields.used.in.expressions.with.their.getters"));
    component.getLabel().setDisplayedMnemonic('u');
    component.setLabelLocation(BorderLayout.NORTH);
    component.setBorder(JBUI.Borders.empty(3, 3, 2, 2));
    return component;
  }

  @Override
  protected int getReplaceFieldsWithGetters() {
    return myReplaceFieldsCb != null
           ? (Integer)myReplaceFieldsCb.getSelectedItem()
           : IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE;
  }

  @Override
  protected TypeSelectorManager getTypeSelectionManager() {
    return myTypeSelectorManager;
  }

  public boolean isGenerateFinal() {
    return hasFinalModifier();
  }

  public void appendOccurrencesDelegate(JPanel myWholePanel) {
    final GridBagConstraints gc =
      new GridBagConstraints(0, 0, 1, 1, 0, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, JBUI.emptyInsets(), 0, 0);
    if (myOccurrences.length > 1 && !myIsInvokedOnDeclaration) {
      gc.gridy++;
      createOccurrencesCb(gc, myWholePanel, myOccurrences.length);
      myCbReplaceAllOccurences.addItemListener(
        new ItemListener() {
          public void itemStateChanged(ItemEvent e) {
            updateControls(new JCheckBox[0]);
          }
        }
      );
    }
    gc.gridy++;
    gc.insets.left = 0;
    createDelegateCb(gc, myWholePanel);
  }

  public boolean hasFinalModifier() {
    if (myHasWriteAccess) return false;
    final Boolean createFinals = JavaRefactoringSettings.getInstance().INTRODUCE_PARAMETER_CREATE_FINALS;
    return createFinals == null ? CodeStyleSettingsManager.getSettings(myProject).GENERATE_FINAL_PARAMETERS : createFinals.booleanValue();
  }
}
