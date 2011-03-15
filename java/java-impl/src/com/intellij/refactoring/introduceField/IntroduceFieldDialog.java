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

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

class IntroduceFieldDialog extends DialogWrapper {


  private final Project myProject;
  private final PsiClass myParentClass;
  private final PsiExpression myInitializerExpression;
  private final PsiLocalVariable myLocalVariable;
  private final boolean myIsCurrentMethodConstructor;
  private final boolean myIsInvokedOnDeclaration;
  private final boolean myWillBeDeclaredStatic;
  private final TypeSelectorManager myTypeSelectorManager;

  private NameSuggestionsField myNameField;


  private TypeSelector myTypeSelector;
  private NameSuggestionsManager myNameSuggestionsManager;
  private static final String REFACTORING_NAME = RefactoringBundle.message("introduce.field.title");
  private final InitializerPlaceChooser myInitializerPlaceChooser;
  private JavaVisibilityPanel myVisibilityPanel;
  private IntroduceFieldPanel myIntroduceFieldPanel;

  public IntroduceFieldDialog(Project project,
                              PsiClass parentClass,
                              PsiExpression initializerExpression,
                              PsiLocalVariable localVariable,
                              boolean isCurrentMethodConstructor, boolean isInvokedOnDeclaration, boolean willBeDeclaredStatic,
                              int occurrencesCount, boolean allowInitInMethod, boolean allowInitInMethodIfAll,
                              TypeSelectorManager typeSelectorManager) {
    super(project, true);
    myProject = project;
    myParentClass = parentClass;
    myInitializerExpression = initializerExpression;
    myInitializerPlaceChooser = new InitializerPlaceChooser(parentClass, initializerExpression, allowInitInMethod, allowInitInMethodIfAll);
    myIntroduceFieldPanel = new IntroduceFieldPanel(isInvokedOnDeclaration, occurrencesCount, localVariable);
    myLocalVariable = localVariable;
    myIsCurrentMethodConstructor = isCurrentMethodConstructor;
    myIsInvokedOnDeclaration = isInvokedOnDeclaration;
    myWillBeDeclaredStatic = willBeDeclaredStatic;

    myTypeSelectorManager = typeSelectorManager;

    setTitle(REFACTORING_NAME);
    init();

    initializeControls(initializerExpression);
    updateButtons();
  }

  private void initializeControls(PsiExpression initializerExpression) {
    myInitializerPlaceChooser.initializeControls(initializerExpression);
    myIntroduceFieldPanel.initializeControls(initializerExpression);

    String ourLastVisibility = JavaRefactoringSettings.getInstance().INTRODUCE_FIELD_VISIBILITY;
    myVisibilityPanel.setVisibility(ourLastVisibility);
  }

  public String getEnteredName() {
    return myNameField.getEnteredName();
  }

  public BaseExpressionToFieldHandler.InitializationPlace getInitializerPlace() {

    return myInitializerPlaceChooser.getInitializerPlace();
  }

  @Modifier
  public String getFieldVisibility() {
    return myVisibilityPanel.getVisibility();
  }

  public boolean isReplaceAllOccurrences() {
    return myIntroduceFieldPanel.isReplaceAllOccurrences();

  }

  public boolean isDeleteVariable() {
    return myIntroduceFieldPanel.isDeleteVariable();

  }

  public boolean isDeclareFinal() {
    return myIntroduceFieldPanel.isDeclareFinal();
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

    myNameSuggestionsManager = new NameSuggestionsManager(myTypeSelector, myNameField, createGenerator());
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
    final JPanel panel = new JPanel(new GridBagLayout());
    final GridBagConstraints gbConstraints = new GridBagConstraints(0, 0, 1, 1, 1, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(0,0,0,0), 0, 0);

    final JPanel mainPanel = new JPanel();
    mainPanel.setLayout(new BorderLayout());

    JPanel groupPanel = new JPanel(new GridLayout(1, 2));

    final JComponent initializerPlacePanel = myInitializerPlaceChooser.createInitializerPlacePanel();
    myInitializerPlaceChooser.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        myIntroduceFieldPanel.updateTypeSelector(myTypeSelectorManager);
        myIntroduceFieldPanel.updateCbFinal(myInitializerPlaceChooser.allowFinal(myWillBeDeclaredStatic, myIsCurrentMethodConstructor));
      }
    });
    groupPanel.add(initializerPlacePanel);

    myVisibilityPanel = new JavaVisibilityPanel(false, false);
    groupPanel.add(myVisibilityPanel);

    mainPanel.add(groupPanel, BorderLayout.CENTER);

    final ItemListener itemListener = new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        if (myIntroduceFieldPanel.hasOccurrences()) {
          myInitializerPlaceChooser.updateInitializerPlace(myIntroduceFieldPanel.isReplaceAllOccurrences());
        }
        myIntroduceFieldPanel.updateTypeSelector(myTypeSelectorManager);
        myNameField.requestFocusInWindow();
      }
    };
    panel.add(mainPanel, gbConstraints);
    myIntroduceFieldPanel.appendFinalCb(panel, gbConstraints, itemListener);
    myIntroduceFieldPanel.appendOccurrencesCb(panel, gbConstraints, itemListener);
    myIntroduceFieldPanel.appendDeleteVariableDeclarationCb(panel, gbConstraints);

    myIntroduceFieldPanel.updateTypeSelector(myTypeSelectorManager);
    return panel;
  }


  private NameSuggestionsGenerator createGenerator() {
    return new NameSuggestionsGenerator() {
      private final JavaCodeStyleManager myCodeStyleManager = JavaCodeStyleManager.getInstance(myProject);
      public SuggestedNameInfo getSuggestedNameInfo(PsiType type) {
        VariableKind variableKind = myWillBeDeclaredStatic ? VariableKind.STATIC_FIELD : VariableKind.FIELD;

        String propertyName = null;
        if (myIsInvokedOnDeclaration) {
          propertyName = myCodeStyleManager.variableNameToPropertyName(myLocalVariable.getName(),
                                                                       VariableKind.LOCAL_VARIABLE
          );
        }
        final SuggestedNameInfo nameInfo = myCodeStyleManager.suggestVariableName(variableKind, propertyName, myInitializerExpression, type);
        final String[] strings = JavaCompletionUtil.completeVariableNameForRefactoring(myCodeStyleManager, type, VariableKind.LOCAL_VARIABLE, nameInfo);
                return new SuggestedNameInfo.Delegate(strings, nameInfo);
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

    IntroduceFieldPanel.ourLastCbFinalState = myIntroduceFieldPanel.isFinal();
    InitializerPlaceChooser.ourLastInitializerPlace = myInitializerPlaceChooser.getInitializerPlace();
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
