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
package com.intellij.refactoring.introduceParameter;

import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.refactoring.IntroduceParameterRefactoring;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.ui.TypeSelectorManager;
import com.intellij.ui.NonFocusableCheckBox;
import com.intellij.ui.StateRestoringCheckBox;
import gnu.trove.TIntArrayList;
import gnu.trove.TIntProcedure;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

/**
 * User: anna
 * Date: 2/27/11
 */
public abstract class IntroduceParameterSettingsUI {
  protected final boolean myIsInvokedOnDeclaration;
  protected final boolean myHasInitializer;

  protected StateRestoringCheckBox myCbDeleteLocalVariable = null;
  protected StateRestoringCheckBox myCbUseInitializer = null;
  protected JRadioButton myReplaceFieldsWithGettersNoneRadio = null;
  protected JRadioButton myReplaceFieldsWithGettersInaccessibleRadio = null;
  protected JRadioButton myReplaceFieldsWithGettersAllRadio = null;
  protected final ButtonGroup myReplaceFieldsWithGettersButtonGroup = new ButtonGroup();
  protected final PsiParameter[] myParametersToRemove;
  protected final boolean[] myParametersToRemoveChecked;
  protected final boolean myIsLocalVariable;

  protected JCheckBox myCbReplaceAllOccurences = null;
  protected JCheckBox myCbGenerateDelegate = null;

  public IntroduceParameterSettingsUI(PsiLocalVariable onLocalVariable,
                                      PsiExpression onExpression,
                                      PsiMethod methodToReplaceIn,
                                      TIntArrayList parametersToRemove) {
    myHasInitializer = onLocalVariable != null && onLocalVariable.getInitializer() != null;
    myIsInvokedOnDeclaration = onExpression == null;
    final PsiParameter[] parameters = methodToReplaceIn.getParameterList().getParameters();
    myParametersToRemove = new PsiParameter[parameters.length];
    myParametersToRemoveChecked = new boolean[parameters.length];
    parametersToRemove.forEach(new TIntProcedure() {
      public boolean execute(final int paramNum) {
        myParametersToRemove[paramNum] = parameters[paramNum];
        return true;
      }
    });
    myIsLocalVariable = onLocalVariable != null;
  }

  protected boolean isDeleteLocalVariable() {
    return myIsInvokedOnDeclaration || myCbDeleteLocalVariable != null && myCbDeleteLocalVariable.isSelected();
  }

  protected boolean isUseInitializer() {
    if(myIsInvokedOnDeclaration)
      return myHasInitializer;
    return myCbUseInitializer != null && myCbUseInitializer.isSelected();
  }

  protected int getReplaceFieldsWithGetters() {
    if(myReplaceFieldsWithGettersAllRadio != null && myReplaceFieldsWithGettersAllRadio.isSelected()) {
      return IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_ALL;
    }
    else if(myReplaceFieldsWithGettersInaccessibleRadio != null
            && myReplaceFieldsWithGettersInaccessibleRadio.isSelected()) {
      return IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE;
    }
    else if(myReplaceFieldsWithGettersNoneRadio != null && myReplaceFieldsWithGettersNoneRadio.isSelected()) {
      return IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE;
    }

    return IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE;
  }

  public boolean isReplaceAllOccurences() {
    return myIsInvokedOnDeclaration || myCbReplaceAllOccurences != null && myCbReplaceAllOccurences.isSelected();
  }

  public boolean isGenerateDelegate() {
    return myCbGenerateDelegate != null && myCbGenerateDelegate.isSelected();
  }

  protected JPanel createReplaceFieldsWithGettersPanel() {
    JPanel radioButtonPanel = new JPanel(new GridBagLayout());

    GridBagConstraints gbConstraints = new GridBagConstraints();
    gbConstraints.insets = new Insets(4, 8, 4, 8);
    gbConstraints.weighty = 1;
    gbConstraints.weightx = 1;
    gbConstraints.gridy = 0;
    gbConstraints.gridwidth = GridBagConstraints.REMAINDER;
    gbConstraints.fill = GridBagConstraints.BOTH;
    gbConstraints.anchor = GridBagConstraints.WEST;
    radioButtonPanel.add(
            new JLabel(RefactoringBundle.message("replace.fields.used.in.expressions.with.their.getters")), gbConstraints);

    myReplaceFieldsWithGettersNoneRadio = new JRadioButton();
    myReplaceFieldsWithGettersNoneRadio.setText(RefactoringBundle.message("do.not.replace"));

    myReplaceFieldsWithGettersInaccessibleRadio = new JRadioButton();
    myReplaceFieldsWithGettersInaccessibleRadio.setText(RefactoringBundle.message("replace.fields.inaccessible.in.usage.context"));

    myReplaceFieldsWithGettersAllRadio = new JRadioButton();
    myReplaceFieldsWithGettersAllRadio.setText(RefactoringBundle.message("replace.all.fields"));

    gbConstraints.gridy++;
    radioButtonPanel.add(myReplaceFieldsWithGettersNoneRadio, gbConstraints);
    gbConstraints.gridy++;
    radioButtonPanel.add(myReplaceFieldsWithGettersInaccessibleRadio, gbConstraints);
    gbConstraints.gridy++;
    radioButtonPanel.add(myReplaceFieldsWithGettersAllRadio, gbConstraints);

    final int currentSetting = JavaRefactoringSettings.getInstance().INTRODUCE_PARAMETER_REPLACE_FIELDS_WITH_GETTERS;

    myReplaceFieldsWithGettersButtonGroup.add(myReplaceFieldsWithGettersNoneRadio);
    myReplaceFieldsWithGettersButtonGroup.add(myReplaceFieldsWithGettersInaccessibleRadio);
    myReplaceFieldsWithGettersButtonGroup.add(myReplaceFieldsWithGettersAllRadio);

    if(currentSetting == IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_ALL) {
      myReplaceFieldsWithGettersAllRadio.setSelected(true);
    }
    else if(currentSetting == IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE) {
      myReplaceFieldsWithGettersInaccessibleRadio.setSelected(true);
    }
    else if(currentSetting == IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE) {
      myReplaceFieldsWithGettersNoneRadio.setSelected(true);
    }

    return radioButtonPanel;
  }

  protected void saveSettings(JavaRefactoringSettings settings) {
    if(myCbDeleteLocalVariable != null) {
      settings.INTRODUCE_PARAMETER_DELETE_LOCAL_VARIABLE =
              myCbDeleteLocalVariable.isSelectedWhenSelectable();
    }

    if (myCbUseInitializer != null) {
      settings.INTRODUCE_PARAMETER_USE_INITIALIZER = myCbUseInitializer.isSelectedWhenSelectable();
    }
  }

  protected TIntArrayList getParametersToRemove() {
    TIntArrayList parameters = new TIntArrayList();
    for (int i = 0; i < myParametersToRemoveChecked.length; i++) {
      if (myParametersToRemoveChecked[i]) {
        parameters.add(i);
      }
    }
    return parameters;
  }

  protected void updateControls(JCheckBox[] removeParamsCb) {
    if (myCbReplaceAllOccurences != null) {
      for (JCheckBox box : removeParamsCb) {
        if (box != null) {
          box.setEnabled(myCbReplaceAllOccurences.isSelected());
          box.setSelected(myCbReplaceAllOccurences.isSelected());
        }
      }
      if (myCbReplaceAllOccurences.isSelected()) {
        if (myCbDeleteLocalVariable != null) {
          myCbDeleteLocalVariable.makeSelectable();
        }
      }
      else {
        if (myCbDeleteLocalVariable != null) {
          myCbDeleteLocalVariable.makeUnselectable(false);
        }
      }
    }
  }

  protected void updateTypeSelector() {
    if (myCbReplaceAllOccurences != null) {
      getTypeSelectionManager().setAllOccurrences(myCbReplaceAllOccurences.isSelected());
    }
    else {
      getTypeSelectionManager().setAllOccurrences(myIsInvokedOnDeclaration);
    }
  }
  
  protected abstract TypeSelectorManager getTypeSelectionManager();

  protected void createRemoveParamsPanel(GridBagConstraints gbConstraints, JPanel panel) {
    final JCheckBox[] removeParamsCb = new JCheckBox[myParametersToRemove.length];
    for (int i = 0; i < myParametersToRemove.length; i++) {
      PsiParameter parameter = myParametersToRemove[i];
      if (parameter == null) continue;
      final NonFocusableCheckBox cb = new NonFocusableCheckBox(RefactoringBundle.message("remove.parameter.0.no.longer.used",
                                                                                         parameter.getName()));
      removeParamsCb[i] = cb;
      cb.setSelected(true);
      gbConstraints.gridy++;
      panel.add(cb, gbConstraints);
      final int i1 = i;
      cb.addChangeListener(new ChangeListener() {
        @Override
        public void stateChanged(ChangeEvent e) {
          myParametersToRemoveChecked[i1] = cb.isSelected();
        }
      });
      myParametersToRemoveChecked[i] = true;
    }

    updateControls(removeParamsCb);
    if (myCbReplaceAllOccurences != null) {
      myCbReplaceAllOccurences.addItemListener(
        new ItemListener() {
          public void itemStateChanged(ItemEvent e) {
            updateControls(removeParamsCb);
          }
        }
      );
    }
  }

  public boolean isParamToRemove(PsiParameter param) {
    if (myCbReplaceAllOccurences != null && !myCbReplaceAllOccurences.isSelected()) {
      return false;
    }
    if (param.isVarArgs()) {
      return myParametersToRemove[myParametersToRemove.length - 1] != null;
    }
    final int parameterIndex = ((PsiMethod)param.getDeclarationScope()).getParameterList().getParameterIndex(param);
    return myParametersToRemove[parameterIndex] != null;
  }

  protected void createLocalVariablePanel(GridBagConstraints gbConstraints, JPanel panel, JavaRefactoringSettings settings) {
    if(myIsLocalVariable && !myIsInvokedOnDeclaration) {
      myCbDeleteLocalVariable = new StateRestoringCheckBox();
      myCbDeleteLocalVariable.setText(RefactoringBundle.message("delete.variable.declaration"));
      myCbDeleteLocalVariable.setFocusable(false);

      gbConstraints.gridy++;
      panel.add(myCbDeleteLocalVariable, gbConstraints);
      myCbDeleteLocalVariable.setSelected(settings.INTRODUCE_PARAMETER_DELETE_LOCAL_VARIABLE);

      gbConstraints.insets = new Insets(0, 0, 4, 8);
      if(myHasInitializer) {
        myCbUseInitializer = new StateRestoringCheckBox();
        myCbUseInitializer.setText(RefactoringBundle.message("use.variable.initializer.to.initialize.parameter"));
        myCbUseInitializer.setSelected(settings.INTRODUCE_PARAMETER_USE_INITIALIZER);
        myCbUseInitializer.setFocusable(false);

        gbConstraints.gridy++;
        panel.add(myCbUseInitializer, gbConstraints);
      }
    }
  }


  protected void createDelegateCb(GridBagConstraints gbConstraints, JPanel panel) {
    myCbGenerateDelegate = new NonFocusableCheckBox(RefactoringBundle.message("delegation.panel.delegate.via.overloading.method"));
    panel.add(myCbGenerateDelegate, gbConstraints);
  }

  protected void createOccurrencesCb(GridBagConstraints gbConstraints, JPanel panel, final int occurenceNumber) {
    myCbReplaceAllOccurences = new NonFocusableCheckBox();
    myCbReplaceAllOccurences.setText(RefactoringBundle.message("replace.all.occurences", occurenceNumber));

    panel.add(myCbReplaceAllOccurences, gbConstraints);
    myCbReplaceAllOccurences.setSelected(false);
  }

  public void setReplaceAllOccurrences(boolean replaceAll) {
    if (myCbReplaceAllOccurences != null) {
      myCbReplaceAllOccurences.setSelected(replaceAll);
    }
  }
}
