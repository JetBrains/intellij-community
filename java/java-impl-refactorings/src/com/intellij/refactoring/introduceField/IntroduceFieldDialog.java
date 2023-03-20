// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.introduceField;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.*;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.ui.*;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.JavaNameSuggestionUtil;
import com.intellij.refactoring.util.RefactoringMessageUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.update.Activatable;
import com.intellij.util.ui.update.UiNotifyConnector;
import org.jetbrains.annotations.Nls;

import javax.swing.*;
import java.awt.*;

class IntroduceFieldDialog extends DialogWrapper {
  public static BaseExpressionToFieldHandler.InitializationPlace ourLastInitializerPlace;

  private final Project myProject;
  private final PsiClass myParentClass;
  private final PsiExpression myInitializerExpression;
  private final String myEnteredName;
  private final PsiLocalVariable myLocalVariable;
  private final boolean myIsInvokedOnDeclaration;
  private final boolean myWillBeDeclaredStatic;
  private final TypeSelectorManager myTypeSelectorManager;

  private NameSuggestionsField myNameField;

  private final IntroduceFieldCentralPanel myCentralPanel;

  private TypeSelector myTypeSelector;
  private NameSuggestionsManager myNameSuggestionsManager;

  IntroduceFieldDialog(Project project,
                              PsiClass parentClass,
                              PsiExpression initializerExpression,
                              PsiLocalVariable localVariable,
                              boolean isCurrentMethodConstructor, boolean isInvokedOnDeclaration, boolean willBeDeclaredStatic,
                              PsiExpression[] occurrences, boolean allowInitInMethod, boolean allowInitInMethodIfAll,
                              TypeSelectorManager typeSelectorManager, String enteredName) {
    super(project, true);
    myProject = project;
    myParentClass = parentClass;
    myInitializerExpression = initializerExpression;
    myEnteredName = enteredName;
    myCentralPanel =
      new IntroduceFieldDialogPanel(parentClass, initializerExpression, localVariable, isCurrentMethodConstructor, isInvokedOnDeclaration,
                                     willBeDeclaredStatic, occurrences, allowInitInMethod, allowInitInMethodIfAll,
                                     typeSelectorManager);
    myLocalVariable = localVariable;
    myIsInvokedOnDeclaration = isInvokedOnDeclaration;
    myWillBeDeclaredStatic = willBeDeclaredStatic;

    myTypeSelectorManager = typeSelectorManager;

    setTitle(getRefactoringName());
    init();

    myCentralPanel.initializeControls(initializerExpression, ourLastInitializerPlace);
    updateButtons();
  }

  public void setReplaceAllOccurrences(boolean replaceAll) {
    myCentralPanel.setReplaceAllOccurrences(replaceAll);
  }

  public String getEnteredName() {
    return myNameField.getEnteredName();
  }

  public BaseExpressionToFieldHandler.InitializationPlace getInitializerPlace() {
    return myCentralPanel.getInitializerPlace();
  }

  @PsiModifier.ModifierConstant
  public String getFieldVisibility() {
    return myCentralPanel.getFieldVisibility();
  }

  public boolean isReplaceAllOccurrences() {
    return myCentralPanel.isReplaceAllOccurrences();
  }

  public boolean isDeleteVariable() {
    return myCentralPanel.isDeleteVariable();
  }

  public boolean isDeclareFinal() {
    return myCentralPanel.isDeclareFinal();
  }

  public PsiType getFieldType() {
    return myTypeSelector.getSelectedType();
  }

  @Override
  protected String getHelpId() {
    return HelpID.INTRODUCE_FIELD;
  }

  @Override
  protected JComponent createNorthPanel() {
    JPanel panel = new JPanel(new GridBagLayout());
    GridBagConstraints gbConstraints = new GridBagConstraints();

    gbConstraints.insets = JBUI.insets(4, 4, 4, 0);
    gbConstraints.anchor = GridBagConstraints.EAST;
    gbConstraints.fill = GridBagConstraints.BOTH;

    gbConstraints.gridwidth = 1;
    gbConstraints.weightx = 0;
    gbConstraints.weighty = 1;
    gbConstraints.gridx = 0;
    gbConstraints.gridy = 0;

    final JLabel type = new JLabel(getTypeLabel());

    panel.add(type, gbConstraints);

    gbConstraints.gridx++;
    gbConstraints.insets = JBUI.insets(4, 0, 4, 4);
    gbConstraints.weightx = 0;
    myTypeSelector = myTypeSelectorManager.getTypeSelector();
    panel.add(myTypeSelector.getComponent(), gbConstraints);

    gbConstraints.insets = JBUI.insets(4, 4, 4, 0);
    gbConstraints.gridwidth = 1;
    gbConstraints.weightx = 0;
    gbConstraints.weighty = 1;
    gbConstraints.gridx = 0;
    gbConstraints.gridy = 1;
    final JLabel namePrompt = new JLabel(RefactoringBundle.message("name.prompt"));
    panel.add(namePrompt, gbConstraints);

    gbConstraints.insets = JBUI.insets(4, 0, 4, 4);
    gbConstraints.gridwidth = 1;
    gbConstraints.weightx = 1;
    gbConstraints.gridx = 1;
    gbConstraints.gridy = 1;
    myNameField = new NameSuggestionsField(myProject);
    panel.add(myNameField.getComponent(), gbConstraints);
    myNameField.addDataChangedListener(() -> updateButtons());
    namePrompt.setLabelFor(myNameField.getFocusableComponent());

    // We delay initialization of name field till dialog is shown, so that it will be executed in a different command and won't
    // be tied to any document changes performed in current command (and won't prevent undo for them later)
    UiNotifyConnector.Once.installOn(panel, new Activatable() {
      @Override
      public void showNotify() {
        myNameSuggestionsManager = new NameSuggestionsManager(myTypeSelector, myNameField,
                                                              JavaNameSuggestionUtil.createFieldNameGenerator(myWillBeDeclaredStatic,
                                                                                                              myLocalVariable,
                                                                                                              myInitializerExpression,
                                                                                                              myIsInvokedOnDeclaration,
                                                                                                              myEnteredName, myParentClass,
                                                                                                              myProject));
        myNameSuggestionsManager.setLabelsFor(type, namePrompt);

        Editor editor = myNameField.getEditor();
        if (editor != null) {
          editor.getSelectionModel().setSelection(0, editor.getDocument().getTextLength());
        }
      }
    });

    return panel;
  }

  private void updateButtons() {
    setOKActionEnabled(PsiNameHelper.getInstance(myProject).isIdentifier(getEnteredName()));
  }

  private @Nls String getTypeLabel() {
    return myWillBeDeclaredStatic ?
           JavaRefactoringBundle.message("introduce.field.static.field.of.type") :
           JavaRefactoringBundle.message("introduce.field.field.of.type");
  }

  @Override
  protected JComponent createCenterPanel() {
    return myCentralPanel.createCenterPanel();
  }

  @Override
  protected void doOKAction() {
    String fieldName = getEnteredName();
    String errorString = null;
    if ("".equals(fieldName)) {
      errorString = RefactoringBundle.message("no.field.name.specified");
    } else if (!PsiNameHelper.getInstance(myProject).isIdentifier(fieldName)) {
      errorString = RefactoringMessageUtil.getIncorrectIdentifierMessage(fieldName);
    }
    if (errorString != null) {
      CommonRefactoringUtil.showErrorMessage(
	IntroduceFieldHandler.getRefactoringNameText(),
	errorString,
	HelpID.INTRODUCE_FIELD,
	myProject
      );
      return;
    }

    PsiField oldField = myParentClass.findFieldByName(fieldName, true);
    if (oldField != null) {
      int answer = Messages.showYesNoDialog(
	myProject,
	RefactoringBundle.message("field.exists", fieldName,
                                   oldField.getContainingClass().getQualifiedName()),
	IntroduceFieldHandler.getRefactoringNameText(),
	Messages.getWarningIcon()
      );
      if (answer != Messages.YES) {
        return;
      }
    }

    myCentralPanel.saveFinalState();
    ourLastInitializerPlace = myCentralPanel.getInitializerPlace();
    JavaRefactoringSettings.getInstance().INTRODUCE_FIELD_VISIBILITY = getFieldVisibility();

    myNameSuggestionsManager.nameSelected();
    myTypeSelectorManager.typeSelected(getFieldType());
    super.doOKAction();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myNameField.getFocusableComponent();
  }

  private static @NlsContexts.DialogTitle String getRefactoringName() {
    return RefactoringBundle.message("introduce.field.title");
  }
}