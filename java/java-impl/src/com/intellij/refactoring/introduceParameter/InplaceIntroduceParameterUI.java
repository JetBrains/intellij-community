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
import java.util.List;

/**
 * User: anna
 */
public abstract class InplaceIntroduceParameterUI extends IntroduceParameterSettingsUI {
  private JComboBox myReplaceFieldsCb;
  private JCheckBox myFinalCb;
  private boolean myHasWriteAccess = false;
  private Project myProject;
  private TypeSelectorManager myTypeSelectorManager;
  private Editor myEditor;
  private PsiExpression[] myOccurrences;
  private List<UsageInfo> myClassMemberRefs;
  private boolean myMustBeFinal;

  public InplaceIntroduceParameterUI(Project project,
                                     PsiLocalVariable onLocalVariable,
                                     PsiExpression onExpression,
                                     PsiMethod methodToReplaceIn,
                                     TIntArrayList parametersToRemove,
                                     TypeSelectorManager typeSelectorManager,
                                     Editor editor,
                                     PsiExpression[] occurrences, List<UsageInfo> classMemberRefs, boolean mustBeFinal) {
    super(project, onLocalVariable, onExpression, methodToReplaceIn, parametersToRemove);
    myProject = project;
    myTypeSelectorManager = typeSelectorManager;
    myEditor = editor;
    myOccurrences = occurrences;
    myClassMemberRefs = classMemberRefs;
    myMustBeFinal = mustBeFinal;

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
    return myFinalCb == null || myFinalCb.isSelected();
  }

  @Override
  protected void updateControls(JCheckBox[] removeParamsCb) {
    super.updateControls(removeParamsCb);
    final boolean writeUsageWouldBeReplaced = writeUsageWouldBeReplaced();
    if (myFinalCb != null) {
      if (writeUsageWouldBeReplaced) {
        myFinalCb.setSelected(false);
      }
      myFinalCb.setEnabled(!writeUsageWouldBeReplaced);
    }
  }

  protected boolean writeUsageWouldBeReplaced() {
    return myHasWriteAccess && isReplaceAllOccurences();
  }

  public void append2MainPanel(JPanel myWholePanel) {
    final GridBagConstraints gc =
      new GridBagConstraints(0, 0, 1, 1, 0, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0);

    gc.insets = new Insets(0, 5, 0, 0);
    gc.gridwidth = 1;
    gc.fill = GridBagConstraints.NONE;
    if (myOccurrences.length > 1 && !myIsInvokedOnDeclaration) {
      gc.gridy++;
      createOccurrencesCb(gc, myWholePanel, myOccurrences.length);
    }
    gc.gridy++;
    gc.insets.left = 5;
    createDelegateCb(gc, myWholePanel);


    final JavaRefactoringSettings settings = JavaRefactoringSettings.getInstance();
    final JPanel rightPanel = new JPanel(new GridBagLayout());
    final GridBagConstraints rgc =
      new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE,
                             new Insets(0, 0, 0, 5), 0, 0);
    createLocalVariablePanel(rgc, rightPanel, settings);
    createRemoveParamsPanel(rgc, rightPanel);
    if (Util.anyFieldsWithGettersPresent(myClassMemberRefs)) {
      rgc.gridy++;
      rightPanel.add(createReplaceFieldsWithGettersPanel(), rgc);
    }

    gc.gridx = 1;
    gc.gridheight = myCbReplaceAllOccurences != null ? 3 : 2;
    gc.gridy = 1;
    myWholePanel.add(rightPanel, gc);

    if (!myMustBeFinal) {
      myFinalCb = new NonFocusableCheckBox("Declare final");
      myFinalCb.setMnemonic('f');
      myFinalCb.setSelected(hasFinalModifier());
      final FinalListener finalListener = new FinalListener(myEditor);
      myFinalCb.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          new WriteCommandAction(myProject, IntroduceParameterHandler.REFACTORING_NAME, IntroduceParameterHandler.REFACTORING_NAME) {
            @Override
            protected void run(Result result) throws Throwable {
              PsiDocumentManager.getInstance(myProject).commitDocument(myEditor.getDocument());
              finalListener.perform(myFinalCb.isSelected(), getParameter(), getBalloon());
            }
          }.execute();
        }
      });
      myWholePanel.add(myFinalCb,
                       new GridBagConstraints(0, myCbReplaceAllOccurences == null ? 2 : 3, 1, 1, 0, 0, GridBagConstraints.NORTHWEST,
                                              GridBagConstraints.NONE, new Insets(0, 5, 2, 5), 0, 0));
    }
  }

  protected abstract Balloon getBalloon();

  @Override
  protected void saveSettings(JavaRefactoringSettings settings) {
    super.saveSettings(settings);
    if (myFinalCb != null && myFinalCb.isEnabled()) {
      settings.INTRODUCE_PARAMETER_CREATE_FINALS = myFinalCb.isSelected();
    }
  }

  public boolean hasFinalModifier() {
    if (myHasWriteAccess) return false;
    final Boolean createFinals = JavaRefactoringSettings.getInstance().INTRODUCE_PARAMETER_CREATE_FINALS;
    return createFinals == null ? CodeStyleSettingsManager.getSettings(myProject).GENERATE_FINAL_PARAMETERS : createFinals.booleanValue();
  }


  public void setReplaceAllOccurrences(boolean replaceAll) {
    if (myCbReplaceAllOccurences != null) {
      myCbReplaceAllOccurences.setSelected(replaceAll);
    }
  }
}
