// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.introduceParameter;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.IntroduceParameterRefactoring;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.introduce.inplace.KeyboardComboSwitcher;
import com.intellij.refactoring.ui.TypeSelectorManager;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import it.unimi.dsi.fastutil.ints.IntList;

import javax.swing.*;
import java.awt.*;

public abstract class InplaceIntroduceParameterUI extends IntroduceParameterSettingsUI {
  private JComboBox<Integer> myReplaceFieldsCb;
  private boolean myHasWriteAccess;
  private final Project myProject;
  private final TypeSelectorManager myTypeSelectorManager;
  private final PsiFile myFile;

  public InplaceIntroduceParameterUI(Project project,
                                     PsiLocalVariable onLocalVariable,
                                     PsiExpression onExpression,
                                     PsiMethod methodToReplaceIn,
                                     IntList parametersToRemove,
                                     TypeSelectorManager typeSelectorManager,
                                     PsiExpression[] occurrences) {
    super(onLocalVariable, onExpression, methodToReplaceIn, parametersToRemove);
    myProject = project;
    myTypeSelectorManager = typeSelectorManager;
    myFile = methodToReplaceIn.getContainingFile();

    for (PsiExpression occurrence : occurrences) {
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
    myReplaceFieldsCb = new ComboBox<>(new Integer[]{IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_ALL,
      IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE,
      IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE});
    myReplaceFieldsCb.setRenderer(SimpleListCellRenderer.create((label, value, index) -> {
      switch (value) {
        case IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE:
          label.setText(UIUtil.removeMnemonic(JavaRefactoringBundle.message("do.not.replace")));
          break;
        case IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE:
          label.setText(UIUtil.removeMnemonic(JavaRefactoringBundle.message("replace.fields.inaccessible.in.usage.context")));
          break;
        default:
          label.setText(UIUtil.removeMnemonic(JavaRefactoringBundle.message("replace.all.fields")));
      }
    }));
    myReplaceFieldsCb.setSelectedItem(JavaRefactoringSettings.getInstance().INTRODUCE_PARAMETER_REPLACE_FIELDS_WITH_GETTERS);
    KeyboardComboSwitcher.setupActions(myReplaceFieldsCb, myProject);
    component.setComponent(myReplaceFieldsCb);
    component.setText(JavaRefactoringBundle.message("replace.fields.used.in.expressions.with.their.getters"));
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

  public boolean hasFinalModifier() {
    if (myHasWriteAccess) return false;
    final Boolean createFinals = JavaRefactoringSettings.getInstance().INTRODUCE_PARAMETER_CREATE_FINALS;
    return createFinals == null ?
           JavaCodeStyleSettings.getInstance(myFile).GENERATE_FINAL_PARAMETERS :
           createFinals.booleanValue();
  }
}
