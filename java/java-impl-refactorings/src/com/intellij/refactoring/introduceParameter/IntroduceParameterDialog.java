// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.introduceParameter;

import com.intellij.codeInspection.AnonymousCanBeLambdaInspection;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.introduceVariable.IntroduceVariableBase;
import com.intellij.refactoring.ui.*;
import com.intellij.ui.NonFocusableCheckBox;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.ui.JBUI;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.List;

public class IntroduceParameterDialog extends RefactoringDialog {
  private static final String INTRODUCE_PARAMETER_LAMBDA = "introduce.parameter.lambda";
  private TypeSelector myTypeSelector;
  private NameSuggestionsManager myNameSuggestionsManager;

  private final PsiFile myFile;
  private final List<? extends UsageInfo> myClassMembersList;
  private final int myOccurenceNumber;
  private final PsiMethod myMethodToSearchFor;
  private final PsiMethod myMethodToReplaceIn;
  private final boolean myMustBeFinal;
  private final PsiExpression myExpression;
  private final PsiLocalVariable myLocalVar;
  protected JCheckBox myCbDeclareFinal;
  protected JCheckBox myCbCollapseToLambda;

  //  private JComponent myParameterNameField = null;
  private NameSuggestionsField myParameterNameField;


  private final NameSuggestionsGenerator myNameSuggestionsGenerator;
  private final TypeSelectorManager myTypeSelectorManager;
  private NameSuggestionsField.DataChanged myParameterNameChangedListener;

  private final IntroduceParameterSettingsPanel myPanel;
  private boolean myHasWriteAccess;
  private IntroduceVariableBase.JavaReplaceChoice myReplaceChoice = IntroduceVariableBase.JavaReplaceChoice.ALL;

  IntroduceParameterDialog(@NotNull Project project,
                           @NotNull List<? extends UsageInfo> classMembersList,
                           PsiExpression[] occurences,
                           PsiLocalVariable onLocalVariable,
                           PsiExpression onExpression,
                           @NotNull NameSuggestionsGenerator generator,
                           @NotNull TypeSelectorManager typeSelectorManager,
                           @NotNull PsiMethod methodToSearchFor,
                           @NotNull PsiMethod methodToReplaceIn,
                           @NotNull IntList parametersToRemove,
                           final boolean mustBeFinal) {
    super(project, true);
    myPanel = new IntroduceParameterSettingsPanel(onLocalVariable, onExpression, methodToReplaceIn, parametersToRemove);
    myFile = methodToReplaceIn.getContainingFile();
    myClassMembersList = classMembersList;
    myOccurenceNumber = occurences.length;
    for (PsiExpression occurence : occurences) {
      if (PsiUtil.isAccessedForWriting(occurence)) {
        myHasWriteAccess = true;
        break;
      }
    }
    myExpression = onExpression;
    myLocalVar = onLocalVariable;
    myMethodToReplaceIn = methodToReplaceIn;
    myMustBeFinal = mustBeFinal;
    myMethodToSearchFor = methodToSearchFor;
    myNameSuggestionsGenerator = generator;
    myTypeSelectorManager = typeSelectorManager;
    setTitle(getRefactoringName());
    init();
    myPanel.updateTypeSelector();
  }

  @Override
  protected void dispose() {
    myParameterNameField.removeDataChangedListener(myParameterNameChangedListener);
    super.dispose();
  }

  private boolean isDeclareFinal() {
    return myCbDeclareFinal != null && myCbDeclareFinal.isSelected();
  }



  @NotNull
  private String getParameterName() {
    return myParameterNameField.getEnteredName().trim();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myParameterNameField.getFocusableComponent();
  }

  private PsiType getSelectedType() {
    return myTypeSelector.getSelectedType();
  }

  @Override
  protected String getHelpId() {
    return HelpID.INTRODUCE_PARAMETER;
  }

  @Override
  protected JComponent createNorthPanel() {
    GridBagConstraints gbConstraints = new GridBagConstraints();

    JPanel panel = new JPanel(new GridBagLayout());

    gbConstraints.anchor = GridBagConstraints.WEST;
    gbConstraints.fill = GridBagConstraints.NONE;
    gbConstraints.gridx = 0;

    gbConstraints.insets = JBUI.insets(4, 4, 4, 0);
    gbConstraints.gridwidth = 1;
    gbConstraints.weightx = 0;
    gbConstraints.weighty = 0;
    gbConstraints.gridy = 0;
    JLabel type = new JLabel(JavaRefactoringBundle.message("parameter.of.type"));
    panel.add(type, gbConstraints);

    gbConstraints.insets = JBUI.insets(4, 4, 4, 8);
    gbConstraints.gridx++;
    gbConstraints.weightx = 1;
    gbConstraints.fill = GridBagConstraints.BOTH;
    myTypeSelector = myTypeSelectorManager.getTypeSelector();
    panel.add(myTypeSelector.getComponent(), gbConstraints);


    gbConstraints.insets = JBUI.insets(4, 4, 4, 8);
    gbConstraints.gridwidth = 1;
    gbConstraints.weightx = 0;
    gbConstraints.gridx = 0;
    gbConstraints.gridy = 1;
    gbConstraints.fill = GridBagConstraints.NONE;

    myParameterNameField = new NameSuggestionsField(myProject);
    final JLabel nameLabel = new JLabel(RefactoringBundle.message("name.prompt"));
    nameLabel.setLabelFor(myParameterNameField.getComponent());
    panel.add(nameLabel, gbConstraints);

/*
    if (myNameSuggestions.length > 1) {
      myParameterNameField = createComboBoxForName();
    }
    else {
      myParameterNameField = createTextFieldForName();
    }
*/
    gbConstraints.gridx++;
    gbConstraints.insets = JBUI.insets(4, 4, 4, 8);
    gbConstraints.weightx = 1;
    gbConstraints.fill = GridBagConstraints.BOTH;
    panel.add(myParameterNameField.getComponent(), gbConstraints);
    myParameterNameChangedListener = () -> validateButtons();
    myParameterNameField.addDataChangedListener(myParameterNameChangedListener);

    myNameSuggestionsManager =
            new NameSuggestionsManager(myTypeSelector, myParameterNameField, myNameSuggestionsGenerator);
    myNameSuggestionsManager.setLabelsFor(type, nameLabel);

    gbConstraints.gridx = 0;
    gbConstraints.insets = JBUI.insets(4, 0, 4, 8);
    gbConstraints.gridwidth = 2;
    if (myOccurenceNumber > 1 && !myPanel.myIsInvokedOnDeclaration) {
      gbConstraints.gridy++;
      myPanel.createOccurrencesCb(gbConstraints, panel, myOccurenceNumber);
    }
    if(myPanel.myCbReplaceAllOccurences != null) {
      gbConstraints.insets = JBUI.insets(0, 16, 4, 8);
    }
    JavaRefactoringSettings settings = JavaRefactoringSettings.getInstance();
    myPanel.createLocalVariablePanel(gbConstraints, panel, settings);

    myPanel.createRemoveParamsPanel(gbConstraints, panel);
    gbConstraints.insets = JBUI.insets(4, 0, 4, 8);

    gbConstraints.gridy++;
    myCbDeclareFinal = new NonFocusableCheckBox(JavaRefactoringBundle.message("declare.final"));

    final Boolean settingsFinals = settings.INTRODUCE_PARAMETER_CREATE_FINALS;
    myCbDeclareFinal.setSelected(settingsFinals == null ?
                                 JavaCodeStyleSettings.getInstance(myFile).GENERATE_FINAL_PARAMETERS :
                                 settingsFinals.booleanValue());
    panel.add(myCbDeclareFinal, gbConstraints);
    if (myMustBeFinal) {
      myCbDeclareFinal.setSelected(true);
      myCbDeclareFinal.setEnabled(false);
    } else if (myHasWriteAccess && myPanel.isReplaceAllOccurences()) {
      myCbDeclareFinal.setSelected(false);
      myCbDeclareFinal.setEnabled(false);
    }

    gbConstraints.gridy++;
    myPanel.createDelegateCb(gbConstraints, panel);

    myCbCollapseToLambda = new NonFocusableCheckBox(JavaRefactoringBundle.message("introduce.parameter.convert.lambda"));
    final PsiAnonymousClass anonymClass = myExpression instanceof PsiNewExpression ? ((PsiNewExpression)myExpression).getAnonymousClass()
                                                                                   : null;
    myCbCollapseToLambda.setVisible(anonymClass != null && AnonymousCanBeLambdaInspection.isLambdaForm(anonymClass, false, Collections.emptySet()));
    myCbCollapseToLambda.setSelected(PropertiesComponent.getInstance(myProject).getBoolean(INTRODUCE_PARAMETER_LAMBDA, true));
    gbConstraints.gridy++;
    panel.add(myCbCollapseToLambda, gbConstraints);

    return panel;
  }


  @Override
  protected JComponent createCenterPanel() {
    if(Util.anyFieldsWithGettersPresent(myClassMembersList)) {
      return myPanel.createReplaceFieldsWithGettersPanel();
    }
    else
      return null;
  }

  @Override
  protected void doAction() {
    final JavaRefactoringSettings settings = JavaRefactoringSettings.getInstance();
    settings.INTRODUCE_PARAMETER_REPLACE_FIELDS_WITH_GETTERS =
            myPanel.getReplaceFieldsWithGetters();
    if (myCbDeclareFinal != null && myCbDeclareFinal.isEnabled()) {
      settings.INTRODUCE_PARAMETER_CREATE_FINALS = Boolean.valueOf(myCbDeclareFinal.isSelected());
    }
    if (myCbCollapseToLambda.isVisible()) {
      PropertiesComponent.getInstance(myProject).setValue(INTRODUCE_PARAMETER_LAMBDA, myCbCollapseToLambda.isSelected());
    }

    myPanel.saveSettings(settings);

    myNameSuggestionsManager.nameSelected();

    boolean isDeleteLocalVariable = false;

    PsiExpression parameterInitializer = myExpression;
    if (myLocalVar != null) {
      if (myPanel.isUseInitializer()) {
        parameterInitializer = myLocalVar.getInitializer();
      }
      isDeleteLocalVariable = myPanel.isDeleteLocalVariable();
    }

    final PsiType selectedType = getSelectedType();
    final IntroduceParameterProcessor processor = new IntroduceParameterProcessor(
      myProject, myMethodToReplaceIn, myMethodToSearchFor,
      parameterInitializer, myExpression,
      myLocalVar, isDeleteLocalVariable,
      getParameterName(), myPanel.isReplaceAllOccurences() ? myReplaceChoice : IntroduceVariableBase.JavaReplaceChoice.NO,
      myPanel.getReplaceFieldsWithGetters(), isDeclareFinal(), myPanel.isGenerateDelegate(),
      myCbCollapseToLambda.isVisible() && myCbCollapseToLambda.isSelected(), selectedType, myPanel.getParametersToRemove());
    invokeRefactoring(processor);
  }


  private void updateFinalState() {
     if (myHasWriteAccess && myCbDeclareFinal != null) {
       myCbDeclareFinal.setEnabled(!myPanel.isReplaceAllOccurences());
       if (myPanel.isReplaceAllOccurences()) {
         myCbDeclareFinal.setSelected(false);
       }
     }
  }

  @Override
  protected void canRun() throws ConfigurationException {
    String name = getParameterName();
    if (!PsiNameHelper.getInstance(myProject).isIdentifier(name)) {
      throw new ConfigurationException(RefactoringBundle.message("refactoring.introduce.parameter.invalid.name", name));
    }
  }

  public void setReplaceAllOccurrences(IntroduceVariableBase.JavaReplaceChoice replaceChoice) {
    myReplaceChoice = replaceChoice;
    myPanel.setReplaceAllOccurrences(replaceChoice.isAll());
  }

  public void setGenerateDelegate(boolean delegate) {
    myPanel.setGenerateDelegate(delegate);
  }

  private class IntroduceParameterSettingsPanel extends IntroduceParameterSettingsUI {
    IntroduceParameterSettingsPanel(PsiLocalVariable onLocalVariable,
                                           PsiExpression onExpression,
                                           PsiMethod methodToReplaceIn, IntList parametersToRemove) {
      super(onLocalVariable, onExpression, methodToReplaceIn, parametersToRemove);
    }


    @Override
    protected TypeSelectorManager getTypeSelectionManager() {
      return myTypeSelectorManager;
    }
    @Override
    protected void updateControls(JCheckBox[] removeParamsCb) {
      super.updateControls(removeParamsCb);
      updateFinalState();
    }

    public void setGenerateDelegate(boolean delegate) {
      myCbGenerateDelegate.setSelected(delegate);
    }
  }

  private static @NlsContexts.DialogTitle String getRefactoringName() {
    return RefactoringBundle.message("introduce.parameter.title");
  }
}