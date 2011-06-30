package com.intellij.refactoring.introduceParameter;

import com.intellij.ide.ui.ListCellRendererWrapper;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.IntroduceParameterRefactoring;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.introduceField.InplaceCombosUtil;
import com.intellij.refactoring.introduceVariable.FinalListener;
import com.intellij.refactoring.ui.TypeSelectorManager;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.NonFocusableCheckBox;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.ui.UIUtil;
import gnu.trove.TIntArrayList;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.List;

/**
 * User: anna
 */
public abstract class InplaceIntroduceParameterUI extends IntroduceParameterSettingsUI {
  private JComboBox myReplaceFieldsCb;
  private boolean myHasWriteAccess = false;
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
    super(project, onLocalVariable, onExpression, methodToReplaceIn, parametersToRemove);
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
    final LabeledComponent<JComboBox> component = new LabeledComponent<JComboBox>();
    myReplaceFieldsCb = new JComboBox(new Integer[]{IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_ALL,
      IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE,
      IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE});
    myReplaceFieldsCb.setRenderer(new ListCellRendererWrapper<Integer>(myReplaceFieldsCb) {
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
    InplaceCombosUtil.appendActions(myReplaceFieldsCb, myProject);
    component.setComponent(myReplaceFieldsCb);
    component.setText(RefactoringBundle.message("replace.fields.used.in.expressions.with.their.getters"));
    component.getLabel().setDisplayedMnemonic('u');
    component.setLabelLocation(BorderLayout.NORTH);
    component.setBorder(IdeBorderFactory.createEmptyBorder(3, 3, 2, 2));
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
      new GridBagConstraints(0, 0, 1, 1, 0, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0);
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
