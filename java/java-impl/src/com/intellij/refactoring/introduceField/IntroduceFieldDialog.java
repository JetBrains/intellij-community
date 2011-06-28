/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.codeInsight.completion.JavaCompletionUtil;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.ui.*;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.RefactoringMessageUtil;
import com.intellij.util.ArrayUtil;

import javax.swing.*;
import java.awt.*;

class IntroduceFieldDialog extends DialogWrapper {


  static BaseExpressionToFieldHandler.InitializationPlace ourLastInitializerPlace;

  private final Project myProject;
  private final PsiClass myParentClass;
  private final PsiExpression myInitializerExpression;
  private String myEnteredName;
  private final PsiLocalVariable myLocalVariable;
  private final boolean myIsInvokedOnDeclaration;
  private final boolean myWillBeDeclaredStatic;
  private final TypeSelectorManager myTypeSelectorManager;

  private NameSuggestionsField myNameField;

  private IntroduceFieldCentralPanel myCentralPanel;

  private TypeSelector myTypeSelector;
  private NameSuggestionsManager myNameSuggestionsManager;
  private static final String REFACTORING_NAME = RefactoringBundle.message("introduce.field.title");

  public IntroduceFieldDialog(Project project,
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

    setTitle(REFACTORING_NAME);
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

  @Modifier
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


  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction(), getHelpAction()};
  }


  protected JComponent createNorthPanel() {

    JPanel panel = new JPanel(new GridBagLayout());
    GridBagConstraints gbConstraints = new GridBagConstraints();

    gbConstraints.insets = new Insets(4, 4, 4, 0);
    gbConstraints.anchor = GridBagConstraints.EAST;
    gbConstraints.fill = GridBagConstraints.BOTH;

    gbConstraints.gridwidth = 1;
    gbConstraints.weightx = 0;
    gbConstraints.weighty = 1;
    gbConstraints.gridx = 0;
    gbConstraints.gridy = 0;

    JLabel type = new JLabel(getTypeLabel());

    panel.add(type, gbConstraints);

    gbConstraints.gridx++;
    gbConstraints.insets = new Insets(4, 0, 4, 4);
    gbConstraints.weightx = 0;
    myTypeSelector = myTypeSelectorManager.getTypeSelector();
    panel.add(myTypeSelector.getComponent(), gbConstraints);

    gbConstraints.insets = new Insets(4, 4, 4, 0);
    gbConstraints.gridwidth = 1;
    gbConstraints.weightx = 0;
    gbConstraints.weighty = 1;
    gbConstraints.gridx = 0;
    gbConstraints.gridy = 1;
    JLabel namePrompt = new JLabel(RefactoringBundle.message("name.prompt"));
    panel.add(namePrompt, gbConstraints);

    gbConstraints.insets = new Insets(4, 0, 4, 4);
    gbConstraints.gridwidth = 1;
    gbConstraints.weightx = 1;
    gbConstraints.gridx = 1;
    gbConstraints.gridy = 1;
    myNameField = new NameSuggestionsField(myProject);
    panel.add(myNameField.getComponent(), gbConstraints);
    myNameField.addDataChangedListener(new NameSuggestionsField.DataChanged() {
      public void dataChanged() {
        updateButtons();
      }
    });
    namePrompt.setLabelFor(myNameField.getFocusableComponent());

    myNameSuggestionsManager = new NameSuggestionsManager(myTypeSelector, myNameField,
                                                          createGenerator(myWillBeDeclaredStatic, myLocalVariable, myInitializerExpression, myIsInvokedOnDeclaration, myEnteredName));
    myNameSuggestionsManager.setLabelsFor(type, namePrompt);

    return panel;
  }

  private void updateButtons() {
    setOKActionEnabled(JavaPsiFacade.getInstance(myProject).getNameHelper().isIdentifier(getEnteredName()));
  }

  private String getTypeLabel() {
    return myWillBeDeclaredStatic ?
           RefactoringBundle.message("introduce.field.static.field.of.type") :
           RefactoringBundle.message("introduce.field.field.of.type");
  }

  protected JComponent createCenterPanel() {
    return myCentralPanel.createCenterPanel();
  }

  static NameSuggestionsGenerator createGenerator(final boolean willBeDeclaredStatic,
                                                  final PsiLocalVariable localVariable,
                                                  final PsiExpression initializerExpression,
                                                  final boolean isInvokedOnDeclaration, final String enteredName) {
    return new NameSuggestionsGenerator() {
      private final JavaCodeStyleManager myCodeStyleManager = JavaCodeStyleManager.getInstance(localVariable != null ? localVariable.getProject()
                                                                                                                     : initializerExpression.getProject());
      public SuggestedNameInfo getSuggestedNameInfo(PsiType type) {
        VariableKind variableKind = willBeDeclaredStatic ? VariableKind.STATIC_FIELD : VariableKind.FIELD;

        String propertyName = null;
        if (isInvokedOnDeclaration) {
          propertyName = myCodeStyleManager.variableNameToPropertyName(localVariable.getName(), VariableKind.LOCAL_VARIABLE);
        }
        final SuggestedNameInfo nameInfo = myCodeStyleManager.suggestVariableName(variableKind, propertyName, initializerExpression, type);
        final String[] strings = JavaCompletionUtil.completeVariableNameForRefactoring(myCodeStyleManager, type, VariableKind.LOCAL_VARIABLE, nameInfo);
        return new SuggestedNameInfo.Delegate(enteredName != null ? ArrayUtil.mergeArrays(new String[]{enteredName}, strings) : strings, nameInfo);
      }
    };
  }


  protected void doOKAction() {
    String fieldName = getEnteredName();
    String errorString = null;
    if ("".equals(fieldName)) {
      errorString = RefactoringBundle.message("no.field.name.specified");
    } else if (!JavaPsiFacade.getInstance(myProject).getNameHelper().isIdentifier(fieldName)) {
      errorString = RefactoringMessageUtil.getIncorrectIdentifierMessage(fieldName);
    }
    if (errorString != null) {
      CommonRefactoringUtil.showErrorMessage(
              IntroduceFieldHandler.REFACTORING_NAME,
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
              IntroduceFieldHandler.REFACTORING_NAME,
              Messages.getWarningIcon()
      );
      if (answer != 0) {
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

  public JComponent getPreferredFocusedComponent() {
    return myNameField.getFocusableComponent();
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(HelpID.INTRODUCE_FIELD);
  }
}
