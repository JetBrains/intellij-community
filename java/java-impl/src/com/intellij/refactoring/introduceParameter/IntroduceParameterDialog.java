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

/*
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: 06.05.2002
 * Time: 16:54:19
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.refactoring.introduceParameter;

import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.IntroduceParameterRefactoring;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.ui.*;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.NonFocusableCheckBox;
import com.intellij.ui.StateRestoringCheckBox;
import com.intellij.usageView.UsageInfo;
import gnu.trove.TIntArrayList;
import gnu.trove.TIntProcedure;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.List;

public class IntroduceParameterDialog extends RefactoringDialog {
  private TypeSelector myTypeSelector;
  private NameSuggestionsManager myNameSuggestionsManager;

  private final Project myProject;
  private final List<UsageInfo> myClassMembersList;
  private final int myOccurenceNumber;
  private final boolean myIsInvokedOnDeclaration;
  private final PsiMethod myMethodToSearchFor;
  private final PsiMethod myMethodToReplaceIn;
  private final boolean myMustBeFinal;
  private final PsiParameter[] myParametersToRemove;
  private final boolean[] myParametersToRemoveChecked;
  private final PsiExpression myExpression;
  private final PsiLocalVariable myLocalVar;
  private final boolean myIsLocalVariable;
  private final boolean myHasInitializer;

//  private JComponent myParameterNameField = null;
  private NameSuggestionsField myParameterNameField;
  private JCheckBox myCbReplaceAllOccurences = null;
  private JCheckBox myCbDeclareFinal = null;
  private JCheckBox myCbGenerateDelegate = null;
  private StateRestoringCheckBox myCbDeleteLocalVariable = null;
  private StateRestoringCheckBox myCbUseInitializer = null;

  private JRadioButton myReplaceFieldsWithGettersNoneRadio = null;
  private JRadioButton myReplaceFieldsWithGettersInaccessibleRadio = null;
  private JRadioButton myReplaceFieldsWithGettersAllRadio = null;

  private final ButtonGroup myReplaceFieldsWithGettersButtonGroup = new ButtonGroup();

  private final NameSuggestionsGenerator myNameSuggestionsGenerator;
  private final TypeSelectorManager myTypeSelectorManager;
  private static final String REFACTORING_NAME = RefactoringBundle.message("introduce.parameter.title");
  private NameSuggestionsField.DataChanged myParameterNameChangedListener;


  IntroduceParameterDialog(@NotNull Project project,
                           @NotNull List<UsageInfo> classMembersList,
                           int occurenceNumber,
                           PsiLocalVariable onLocalVariable,
                           PsiExpression onExpression,
                           @NotNull NameSuggestionsGenerator generator,
                           @NotNull TypeSelectorManager typeSelectorManager,
                           @NotNull PsiMethod methodToSearchFor,
                           @NotNull PsiMethod methodToReplaceIn,
                           @NotNull TIntArrayList parametersToRemove,
                           final boolean mustBeFinal) {
    super(project, true);
    myProject = project;
    myClassMembersList = classMembersList;
    myOccurenceNumber = occurenceNumber;
    myExpression = onExpression;
    myLocalVar = onLocalVariable;
    myMethodToReplaceIn = methodToReplaceIn;
    myMustBeFinal = mustBeFinal;
    myIsInvokedOnDeclaration = onExpression == null;
    myMethodToSearchFor = methodToSearchFor;
    myIsLocalVariable = onLocalVariable != null;
    myHasInitializer = onLocalVariable != null && onLocalVariable.getInitializer() != null;
    myNameSuggestionsGenerator = generator;
    myTypeSelectorManager = typeSelectorManager;
    final PsiParameter[] parameters = methodToReplaceIn.getParameterList().getParameters();
    myParametersToRemove = new PsiParameter[parameters.length];
    myParametersToRemoveChecked = new boolean[parameters.length];
    parametersToRemove.forEach(new TIntProcedure() {
      public boolean execute(final int paramNum) {
        myParametersToRemove[paramNum] = parameters[paramNum];
        return true;
      }
    });

    setTitle(REFACTORING_NAME);
    init();
  }

  protected void dispose() {
    myParameterNameField.removeDataChangedListener(myParameterNameChangedListener);
    super.dispose();
  }

  private boolean isDeclareFinal() {
    return myCbDeclareFinal != null && myCbDeclareFinal.isSelected();
  }

  private boolean isReplaceAllOccurences() {
    return myIsInvokedOnDeclaration || myCbReplaceAllOccurences != null && myCbReplaceAllOccurences.isSelected();
  }

  private boolean isDeleteLocalVariable() {
    return myIsInvokedOnDeclaration || myCbDeleteLocalVariable != null && myCbDeleteLocalVariable.isSelected();
  }

  private boolean isUseInitializer() {
    if(myIsInvokedOnDeclaration)
      return myHasInitializer;
    return myCbUseInitializer != null && myCbUseInitializer.isSelected();
  }

  private boolean isGenerateDelegate() {
    return myCbGenerateDelegate != null && myCbGenerateDelegate.isSelected();
  }

  private String getParameterName() {
    return  myParameterNameField.getEnteredName();
  }

  private int getReplaceFieldsWithGetters() {
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

  public JComponent getPreferredFocusedComponent() {
    return myParameterNameField.getFocusableComponent();
  }

  private PsiType getSelectedType() {
    return myTypeSelector.getSelectedType();
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(HelpID.INTRODUCE_PARAMETER);
  }

  protected JComponent createNorthPanel() {
    GridBagConstraints gbConstraints = new GridBagConstraints();

    JPanel panel = new JPanel(new GridBagLayout());

    gbConstraints.anchor = GridBagConstraints.WEST;
    gbConstraints.fill = GridBagConstraints.NONE;
    gbConstraints.gridx = 0;

    gbConstraints.insets = new Insets(4, 4, 4, 0);
    gbConstraints.gridwidth = 1;
    gbConstraints.weightx = 0;
    gbConstraints.weighty = 0;
    gbConstraints.gridy = 0;
    JLabel type = new JLabel(RefactoringBundle.message("parameter.of.type"));
    panel.add(type, gbConstraints);

    gbConstraints.insets = new Insets(4, 4, 4, 8);
    gbConstraints.gridx++;
    gbConstraints.weightx = 1;
    gbConstraints.fill = GridBagConstraints.BOTH;
    myTypeSelector = myTypeSelectorManager.getTypeSelector();
    panel.add(myTypeSelector.getComponent(), gbConstraints);


    gbConstraints.insets = new Insets(4, 4, 4, 8);
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
    gbConstraints.insets = new Insets(4, 4, 4, 8);
    gbConstraints.weightx = 1;
    gbConstraints.fill = GridBagConstraints.BOTH;
    panel.add(myParameterNameField.getComponent(), gbConstraints);
    myParameterNameChangedListener = new NameSuggestionsField.DataChanged() {
      public void dataChanged() {
        validateButtons();
      }
    };
    myParameterNameField.addDataChangedListener(myParameterNameChangedListener);

    myNameSuggestionsManager =
            new NameSuggestionsManager(myTypeSelector, myParameterNameField, myNameSuggestionsGenerator);
    myNameSuggestionsManager.setLabelsFor(type, nameLabel);

    gbConstraints.gridx = 0;
    gbConstraints.insets = new Insets(4, 0, 4, 8);
    gbConstraints.gridwidth = 2;
    if (myOccurenceNumber > 1 && !myIsInvokedOnDeclaration) {
      gbConstraints.gridy++;
      myCbReplaceAllOccurences = new NonFocusableCheckBox();
      myCbReplaceAllOccurences.setText(RefactoringBundle.message("replace.all.occurences", myOccurenceNumber));

      panel.add(myCbReplaceAllOccurences, gbConstraints);
      myCbReplaceAllOccurences.setSelected(false);
    }

    JavaRefactoringSettings settings = JavaRefactoringSettings.getInstance();

    gbConstraints.gridy++;
    myCbDeclareFinal = new NonFocusableCheckBox(RefactoringBundle.message("declare.final"));

    final Boolean settingsFinals = settings.INTRODUCE_PARAMETER_CREATE_FINALS;
    myCbDeclareFinal.setSelected(settingsFinals == null ?
                                 CodeStyleSettingsManager.getSettings(myProject).GENERATE_FINAL_PARAMETERS :
                                 settingsFinals.booleanValue());
    panel.add(myCbDeclareFinal, gbConstraints);
    if (myMustBeFinal) {
      myCbDeclareFinal.setSelected(true);
      myCbDeclareFinal.setEnabled(false);
    }


    if(myIsLocalVariable && !myIsInvokedOnDeclaration) {
      myCbDeleteLocalVariable = new StateRestoringCheckBox();
      myCbDeleteLocalVariable.setText(RefactoringBundle.message("delete.variable.definition"));

      if(myCbReplaceAllOccurences != null) {
        gbConstraints.insets = new Insets(0, 16, 4, 8);
      }
      gbConstraints.gridy++;
      panel.add(myCbDeleteLocalVariable, gbConstraints);
      myCbDeleteLocalVariable.setSelected(settings.INTRODUCE_PARAMETER_DELETE_LOCAL_VARIABLE);

      gbConstraints.insets = new Insets(4, 0, 4, 8);
      if(myHasInitializer) {
        myCbUseInitializer = new StateRestoringCheckBox();
        myCbUseInitializer.setText(RefactoringBundle.message("use.variable.initializer.to.initialize.parameter"));
        myCbUseInitializer.setSelected(settings.INTRODUCE_PARAMETER_USE_INITIALIZER);

        gbConstraints.gridy++;
        panel.add(myCbUseInitializer, gbConstraints);
      }
    }

    gbConstraints.gridy++;
    myCbGenerateDelegate = new NonFocusableCheckBox(RefactoringBundle.message("delegation.panel.delegate.via.overloading.method"));
    panel.add(myCbGenerateDelegate, gbConstraints);

    for (int i = 0; i < myParametersToRemove.length; i++) {
      PsiParameter parameter = myParametersToRemove[i];
      if (parameter == null) continue;
      final NonFocusableCheckBox cb = new NonFocusableCheckBox(RefactoringBundle.message("remove.parameter.0.no.longer.used",
                                                                                         parameter.getName()));
      cb.setSelected(true);
      gbConstraints.gridy++;
      panel.add(cb, gbConstraints);
      final int i1 = i;
      cb.addActionListener(new ActionListener() {
        public void actionPerformed(final ActionEvent e) {
          myParametersToRemoveChecked[i1] = cb.isSelected();
        }
      });
      myParametersToRemoveChecked[i] = true;
    }

    updateControls();
    if (myCbReplaceAllOccurences != null) {
      myCbReplaceAllOccurences.addItemListener(
              new ItemListener() {
                public void itemStateChanged(ItemEvent e) {
                  updateControls();
                }
              }
      );
    }
    return panel;
  }

  private void updateControls() {
    if(myCbReplaceAllOccurences != null) {
      myTypeSelectorManager.setAllOccurences(myCbReplaceAllOccurences.isSelected());
      if(myCbReplaceAllOccurences.isSelected()) {
        if (myCbDeleteLocalVariable != null) {
          myCbDeleteLocalVariable.makeSelectable();
        }
      }
      else {
        if (myCbDeleteLocalVariable != null) {
          myCbDeleteLocalVariable.makeUnselectable(false);
        }
      }
    } else {
      myTypeSelectorManager.setAllOccurences(myIsInvokedOnDeclaration);
    }

  }

  private JPanel createReplaceFieldsWithGettersPanel() {
    JPanel radioButtonPanel = new JPanel(new GridBagLayout());

    radioButtonPanel.setBorder(IdeBorderFactory.createRoundedBorder());

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

  protected JComponent createCenterPanel() {
    if(Util.anyFieldsWithGettersPresent(myClassMembersList)) {
      return createReplaceFieldsWithGettersPanel();
    }
    else
      return null;
  }

  protected void doAction() {
    final JavaRefactoringSettings settings = JavaRefactoringSettings.getInstance();
    settings.INTRODUCE_PARAMETER_REPLACE_FIELDS_WITH_GETTERS =
            getReplaceFieldsWithGetters();
    if (myCbDeclareFinal != null && !myMustBeFinal) {
      settings.INTRODUCE_PARAMETER_CREATE_FINALS = Boolean.valueOf(myCbDeclareFinal.isSelected());
    }

    if(myCbDeleteLocalVariable != null) {
      settings.INTRODUCE_PARAMETER_DELETE_LOCAL_VARIABLE =
              myCbDeleteLocalVariable.isSelectedWhenSelectable();
    }

    if (myCbUseInitializer != null) {
      settings.INTRODUCE_PARAMETER_USE_INITIALIZER = myCbUseInitializer.isSelectedWhenSelectable();
    }

    myNameSuggestionsManager.nameSelected();

    boolean isDeleteLocalVariable = false;

    PsiExpression parameterInitializer = myExpression;
    if (myLocalVar != null) {
      if (isUseInitializer()) {
      parameterInitializer = myLocalVar.getInitializer();      }
      isDeleteLocalVariable = isDeleteLocalVariable();
    }

    final IntroduceParameterProcessor processor = new IntroduceParameterProcessor(
      myProject, myMethodToReplaceIn, myMethodToSearchFor,
      parameterInitializer, myExpression,
      myLocalVar, isDeleteLocalVariable,
      getParameterName(), isReplaceAllOccurences(),
      getReplaceFieldsWithGetters(), isDeclareFinal(), isGenerateDelegate(), getSelectedType(), getParametersToRemove());
    invokeRefactoring(processor);
    myParameterNameField.requestFocusInWindow();
  }

  private TIntArrayList getParametersToRemove() {
    TIntArrayList parameters = new TIntArrayList();
    for (int i = 0; i < myParametersToRemoveChecked.length; i++) {
      if (myParametersToRemoveChecked[i]) {
        parameters.add(i);
      }
    }
    return parameters;
  }


  @Override
  protected void canRun() throws ConfigurationException {
    String name = getParameterName();
    if (name == null || !JavaPsiFacade.getInstance(myProject).getNameHelper().isIdentifier(name.trim())) {
      throw new ConfigurationException("\'" + StringUtil.first(name != null ? name : "", 10 , true) + "\' is invalid parameter name");
    }
  }


}
