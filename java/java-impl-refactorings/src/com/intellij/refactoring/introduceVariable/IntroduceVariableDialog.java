// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.introduceVariable;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNameHelper;
import com.intellij.psi.PsiType;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.ui.*;
import com.intellij.ui.NonFocusableCheckBox;
import com.intellij.ui.StateRestoringCheckBox;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

class IntroduceVariableDialog extends DialogWrapper implements IntroduceVariableSettings {
  private final Project myProject;
  private final PsiFile myFile;
  private final PsiExpression myExpression;
  private final int myOccurrencesCount;
  private final boolean myAnyLValueOccurrences;
  private final boolean myDeclareFinalIfAll;
  private final TypeSelectorManager myTypeSelectorManager;
  private final IntroduceVariableHandler.Validator myValidator;

  private NameSuggestionsField myNameField;
  private JCheckBox myCbReplaceAll;
  private StateRestoringCheckBox myCbReplaceWrite;
  private JCheckBox myCbFinal;
  private boolean myCbFinalState;
  private JCheckBox myCbVarType;
  private TypeSelector myTypeSelector;
  private NameSuggestionsManager myNameSuggestionsManager;
  private NameSuggestionsField.DataChanged myNameChangedListener;
  private ItemListener myReplaceAllListener;
  private ItemListener myFinalListener;

  IntroduceVariableDialog(Project project,
                                 PsiExpression expression, int occurrencesCount, boolean anyLValueOccurrences,
                                 boolean declareFinalIfAll, TypeSelectorManager typeSelectorManager,
                                 IntroduceVariableHandler.Validator validator) {
    super(project, true);
    myProject = project;
    myExpression = expression;
    myOccurrencesCount = occurrencesCount;
    myAnyLValueOccurrences = anyLValueOccurrences;
    myDeclareFinalIfAll = declareFinalIfAll;
    myTypeSelectorManager = typeSelectorManager;
    myValidator = validator;
    myFile = expression.getContainingFile();

    setTitle(getRefactoringName());
    init();
  }

  @Override
  protected void dispose() {
    myNameField.removeDataChangedListener(myNameChangedListener);
    if (myCbReplaceAll != null) {
      myCbReplaceAll.removeItemListener(myReplaceAllListener);
    }
    myCbFinal.removeItemListener(myFinalListener);
    super.dispose();
  }

  @Override
  protected void init() {
    super.init();
    updateOkStatus();
  }

  @Override
  public @NlsSafe String getEnteredName() {
    return myNameField.getEnteredName();
  }

  @Override
  public boolean isReplaceAllOccurrences() {
    if (myOccurrencesCount <= 1) return false;
    return myCbReplaceAll.isSelected();
  }

  @Override
  public boolean isDeclareFinal() {
    return myCbFinal.isSelected();
  }

  @Override
  public boolean isReplaceLValues() {
    if (myOccurrencesCount <= 1 || !myAnyLValueOccurrences || myCbReplaceWrite == null) {
      return true;
    }
    else {
      return myCbReplaceWrite.isSelected();
    }
  }

  @Override
  public boolean isDeclareVarType() {
    return myCbVarType.isVisible() && myCbVarType.isEnabled() && myCbVarType.isSelected();
  }

  @Override
  public PsiType getSelectedType() {
    return myTypeSelector.getSelectedType();
  }

  @Override
  protected JComponent createNorthPanel() {
    myNameField = new NameSuggestionsField(myProject);
    myNameChangedListener = () -> updateOkStatus();
    myNameField.addDataChangedListener(myNameChangedListener);

    JPanel panel = new JPanel(new GridBagLayout());
    GridBagConstraints gbConstraints = new GridBagConstraints();

    gbConstraints.insets = JBUI.insets(4);
    gbConstraints.anchor = GridBagConstraints.WEST;
    gbConstraints.fill = GridBagConstraints.BOTH;

    gbConstraints.gridwidth = 1;
    gbConstraints.weightx = 0;
    gbConstraints.weighty = 0;
    gbConstraints.gridx = 0;
    gbConstraints.gridy = 0;
    JLabel type = new JLabel(JavaRefactoringBundle.message("variable.of.type"));
    panel.add(type, gbConstraints);

    gbConstraints.gridx++;
    myTypeSelector = myTypeSelectorManager.getTypeSelector();
    panel.add(myTypeSelector.getComponent(), gbConstraints);

    gbConstraints.gridwidth = 1;
    gbConstraints.weightx = 0;
    gbConstraints.weighty = 0;
    gbConstraints.gridx = 0;
    gbConstraints.gridy = 1;
    JLabel namePrompt = new JLabel(RefactoringBundle.message("name.prompt"));
    namePrompt.setLabelFor(myNameField.getComponent());
    panel.add(namePrompt, gbConstraints);

    gbConstraints.gridwidth = 1;
    gbConstraints.weightx = 1;
    gbConstraints.gridx = 1;
    gbConstraints.gridy = 1;
    panel.add(myNameField.getComponent(), gbConstraints);

    myNameSuggestionsManager = new NameSuggestionsManager(myTypeSelector, myNameField,
            new NameSuggestionsGenerator() {
              @Override
              public SuggestedNameInfo getSuggestedNameInfo(PsiType type) {
                return CommonJavaRefactoringUtil.getSuggestedName(type, myExpression);
              }
            });
    myNameSuggestionsManager.setLabelsFor(type, namePrompt);

    return panel;
  }

  @Override
  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new GridBagLayout());
    GridBagConstraints gbConstraints = new GridBagConstraints();
    gbConstraints.fill = GridBagConstraints.HORIZONTAL;
    gbConstraints.weightx = 1;
    gbConstraints.weighty = 0;
    gbConstraints.gridwidth = 1;
    gbConstraints.gridx = 0;
    gbConstraints.gridy = 0;
    gbConstraints.insets = JBInsets.emptyInsets();

    if (myOccurrencesCount > 1) {
      myCbReplaceAll = new NonFocusableCheckBox();
      myCbReplaceAll.setText(RefactoringBundle.message("replace.all.occurences", myOccurrencesCount));

      panel.add(myCbReplaceAll, gbConstraints);
      myReplaceAllListener = new ItemListener() {
        @Override
        public void itemStateChanged(ItemEvent e) {
          updateControls();
        }
      };
      myCbReplaceAll.addItemListener(myReplaceAllListener);

      if (myAnyLValueOccurrences) {
        myCbReplaceWrite = new StateRestoringCheckBox();
        myCbReplaceWrite.setText(JavaRefactoringBundle.message("replace.write.access.occurrences"));
        gbConstraints.insets = JBUI.insetsLeft(8);
        gbConstraints.gridy++;
        panel.add(myCbReplaceWrite, gbConstraints);
        myCbReplaceWrite.addItemListener(myReplaceAllListener);
      }
    }

    myCbFinal = new NonFocusableCheckBox();
    myCbFinal.setText(JavaRefactoringBundle.message("declare.final"));
    final Boolean createFinals = JavaRefactoringSettings.getInstance().INTRODUCE_LOCAL_CREATE_FINALS;
    myCbFinalState = createFinals == null ?
                     JavaCodeStyleSettings.getInstance(myFile).GENERATE_FINAL_LOCALS :
                     createFinals.booleanValue();

    gbConstraints.insets = JBInsets.emptyInsets();
    gbConstraints.gridy++;
    panel.add(myCbFinal, gbConstraints);
    myFinalListener = new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        if (myCbFinal.isEnabled()) {
          myCbFinalState = myCbFinal.isSelected();
        }
      }
    };
    myCbFinal.addItemListener(myFinalListener);

    myCbVarType = new NonFocusableCheckBox(JavaRefactoringBundle.message("declare.var.type"));
    boolean toVarType = IntroduceVariableBase.canBeExtractedWithoutExplicitType(myExpression);
    if (toVarType) {
      myTypeSelector.addItemListener(new ItemListener() {
        @Override
        public void itemStateChanged(ItemEvent e) {
          if (e.getStateChange() == ItemEvent.SELECTED) {
            myCbVarType.setEnabled(Comparing.equal(myTypeSelector.getSelectedType(), CommonJavaRefactoringUtil.getTypeByExpressionWithExpectedType(myExpression)));
          }
        }
      });
    }
    myCbVarType.setVisible(toVarType);
    myCbVarType.setSelected(IntroduceVariableBase.createVarType());

    gbConstraints.gridy++;
    panel.add(myCbVarType, gbConstraints);

    updateControls();

    return panel;
  }

  private void updateControls() {
    if (myCbReplaceWrite != null) {
      if (myCbReplaceAll.isSelected()) {
        myCbReplaceWrite.makeSelectable();
      } else {
        myCbReplaceWrite.makeUnselectable(true);
      }
    }

    if (myCbReplaceAll != null) {
      myTypeSelectorManager.setAllOccurrences(myCbReplaceAll.isSelected());
    } else {
      myTypeSelectorManager.setAllOccurrences(false);
    }

    if (myDeclareFinalIfAll && myCbReplaceAll != null && myCbReplaceAll.isSelected()) {
      myCbFinal.setEnabled(false);
      myCbFinal.setSelected(true);
    } else if (myCbReplaceWrite != null && myCbReplaceWrite.isEnabled() && myCbReplaceWrite.isSelected()) {
      myCbFinal.setEnabled(false);
      myCbFinal.setSelected(false);
    }
    else {
      myCbFinal.setEnabled(true);
      myCbFinal.setSelected(myCbFinalState);
    }

    if (myCbVarType != null) {
      myCbVarType.setEnabled(Comparing.equal(myTypeSelector.getSelectedType(), myExpression.getType()));
    }
  }

  @Override
  protected void doOKAction() {
    if (!myValidator.isOK(this)) return;
    myNameSuggestionsManager.nameSelected();
    myTypeSelectorManager.typeSelected(getSelectedType());
    if (myCbFinal.isEnabled()) {
      JavaRefactoringSettings.getInstance().INTRODUCE_LOCAL_CREATE_FINALS = myCbFinal.isSelected();
    }
    if (myCbVarType.isVisible() && myCbVarType.isEnabled()) {
      JavaRefactoringSettings.getInstance().INTRODUCE_LOCAL_CREATE_VAR_TYPE = myCbVarType.isSelected();
    }
    super.doOKAction();
  }

  private void updateOkStatus() {
    String text = getEnteredName();
    setOKActionEnabled(PsiNameHelper.getInstance(myProject).isIdentifier(text));
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myNameField.getFocusableComponent();
  }

  @Override
  protected String getHelpId() {
    return HelpID.INTRODUCE_VARIABLE;
  }

  private static @NlsContexts.DialogTitle String getRefactoringName() {
    return RefactoringBundle.message("introduce.variable.title");
  }
}