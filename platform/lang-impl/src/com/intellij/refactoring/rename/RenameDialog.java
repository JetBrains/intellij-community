/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

package com.intellij.refactoring.rename;

import com.intellij.lang.findUsages.DescriptiveNameUtil;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.rename.inplace.VariableInplaceRenameHandler;
import com.intellij.refactoring.rename.naming.AutomaticRenamerFactory;
import com.intellij.refactoring.ui.NameSuggestionsField;
import com.intellij.refactoring.ui.RefactoringDialog;
import com.intellij.refactoring.util.TextOccurrencesUtil;
import com.intellij.ui.NonFocusableCheckBox;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.xml.util.XmlStringUtil;
import com.intellij.xml.util.XmlTagUtilBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;

public class RenameDialog extends RefactoringDialog {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.rename.RenameDialog");
  private SuggestedNameInfo mySuggestedNameInfo;

  private JLabel myNameLabel;
  private NameSuggestionsField myNameSuggestionsField;
  private JCheckBox myCbSearchInComments;
  private JCheckBox myCbSearchTextOccurences;
  private final JLabel myNewNamePrefix = new JLabel("");
  private final String myHelpID;
  @NotNull private final PsiElement myPsiElement;
  private final PsiElement myNameSuggestionContext;
  private final Editor myEditor;
  private static final String REFACTORING_NAME = RefactoringBundle.message("rename.title");
  private NameSuggestionsField.DataChanged myNameChangedListener;
  private final Map<AutomaticRenamerFactory, JCheckBox> myAutomaticRenamers = new HashMap<>();
  private String myOldName;

  public RenameDialog(@NotNull Project project, @NotNull PsiElement psiElement, @Nullable PsiElement nameSuggestionContext,
                      Editor editor) {
    super(project, true);

    assert psiElement.isValid();

    myPsiElement = psiElement;
    myNameSuggestionContext = nameSuggestionContext;
    myEditor = editor;
    setTitle(REFACTORING_NAME);

    createNewNameComponent();
    init();

    myNameLabel.setText(XmlStringUtil.wrapInHtml(XmlTagUtilBase.escapeString(getLabelText(), false)));
    boolean toSearchInComments = isToSearchInCommentsForRename();
    myCbSearchInComments.setSelected(toSearchInComments);

    if (myCbSearchTextOccurences.isEnabled()) {
      boolean toSearchForTextOccurences = isToSearchForTextOccurencesForRename();
      myCbSearchTextOccurences.setSelected(toSearchForTextOccurences);
    }

    if (!ApplicationManager.getApplication().isUnitTestMode()) validateButtons();
    myHelpID = RenamePsiElementProcessor.forElement(psiElement).getHelpID(psiElement);
  }

  public static void showRenameDialog(DataContext dataContext, RenameDialog dialog) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      final String name = PsiElementRenameHandler.DEFAULT_NAME.getData(dataContext);
      //noinspection TestOnlyProblems
      dialog.performRename(name);
      dialog.close(OK_EXIT_CODE);
    }
    else {
      dialog.show();
    }
  }

  @NotNull
  protected String getLabelText() {
    return RefactoringBundle.message("rename.0.and.its.usages.to", getFullName());
  }

  @NotNull
  public PsiElement getPsiElement() {
    return myPsiElement;
  }

  @Override
  protected boolean hasPreviewButton() {
    return RenamePsiElementProcessor.forElement(myPsiElement).showRenamePreviewButton(myPsiElement);
  }

  @Override
  protected void dispose() {
    myNameSuggestionsField.removeDataChangedListener(myNameChangedListener);
    super.dispose();
  }

  protected boolean isToSearchForTextOccurencesForRename() {
    return RenamePsiElementProcessor.forElement(myPsiElement).isToSearchForTextOccurrences(myPsiElement);
  }

  protected boolean isToSearchInCommentsForRename() {
    return RenamePsiElementProcessor.forElement(myPsiElement).isToSearchInComments(myPsiElement);
  }

  protected String getFullName() {
    final String name = DescriptiveNameUtil.getDescriptiveName(myPsiElement);
    return (UsageViewUtil.getType(myPsiElement) + " " + name).trim();
  }

  protected void createNewNameComponent() {
    String[] suggestedNames = getSuggestedNames();
    myOldName = UsageViewUtil.getShortName(myPsiElement);
    myNameSuggestionsField = new NameSuggestionsField(suggestedNames, myProject, FileTypes.PLAIN_TEXT, myEditor) {
      @Override
      protected boolean shouldSelectAll() {
        return myEditor == null || myEditor.getSettings().isPreselectRename();
      }
    };
    if (myPsiElement instanceof PsiFile && myEditor == null) {
      myNameSuggestionsField.selectNameWithoutExtension();
    }
    myNameChangedListener = new NameSuggestionsField.DataChanged() {
      @Override
      public void dataChanged() {
        processNewNameChanged();
      }
    };
    myNameSuggestionsField.addDataChangedListener(myNameChangedListener);

  }

  protected void preselectExtension(int start, int end) {
    myNameSuggestionsField.select(start, end);
  }

  protected void processNewNameChanged() {
    validateButtons();
  }

  public String[] getSuggestedNames() {
    final LinkedHashSet<String> result = new LinkedHashSet<>();
    final String initialName = VariableInplaceRenameHandler.getInitialName();
    if (initialName != null) {
      result.add(initialName);
    }
    result.add(UsageViewUtil.getShortName(myPsiElement));
    final NameSuggestionProvider[] providers = Extensions.getExtensions(NameSuggestionProvider.EP_NAME);
    for(NameSuggestionProvider provider: providers) {
      SuggestedNameInfo info = provider.getSuggestedNames(myPsiElement, myNameSuggestionContext, result);
      if (info != null) {
        mySuggestedNameInfo = info;
        if (provider instanceof PreferrableNameSuggestionProvider && !((PreferrableNameSuggestionProvider)provider).shouldCheckOthers()) break;
      }
    }
    return ArrayUtil.toStringArray(result);
  }


  public String getNewName() {
    return myNameSuggestionsField.getEnteredName().trim();
  }

  public boolean isSearchInComments() {
    return myCbSearchInComments.isSelected();
  }

  public boolean isSearchInNonJavaFiles() {
    return myCbSearchTextOccurences.isSelected();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myNameSuggestionsField.getFocusableComponent();
  }

  @Override
  protected JComponent createCenterPanel() {
    return null;
  }

  @Override
  protected JComponent createNorthPanel() {
    JPanel panel = new JPanel(new GridBagLayout());
    GridBagConstraints gbConstraints = new GridBagConstraints();

    gbConstraints.insets = new Insets(0, 0, 4, 0);
    gbConstraints.weighty = 0;
    gbConstraints.weightx = 1;
    gbConstraints.gridwidth = GridBagConstraints.REMAINDER;
    gbConstraints.fill = GridBagConstraints.BOTH;
    myNameLabel = new JLabel();
    panel.add(myNameLabel, gbConstraints);

    gbConstraints.insets = new Insets(0, 0, 4, "".equals(myNewNamePrefix.getText()) ? 0 : 1);
    gbConstraints.gridwidth = 1;
    gbConstraints.fill = GridBagConstraints.NONE;
    gbConstraints.weightx = 0;
    gbConstraints.gridx = 0;
    gbConstraints.anchor = GridBagConstraints.WEST;
    panel.add(myNewNamePrefix, gbConstraints);

    gbConstraints.insets = new Insets(0, 0, 8, 0);
    gbConstraints.gridwidth = 2;
    gbConstraints.fill = GridBagConstraints.BOTH;
    gbConstraints.weightx = 1;
    gbConstraints.gridx = 0;
    gbConstraints.weighty = 1;
    panel.add(myNameSuggestionsField.getComponent(), gbConstraints);

    createCheckboxes(panel, gbConstraints);

    return panel;
  }

  protected void createCheckboxes(JPanel panel, GridBagConstraints gbConstraints) {
    gbConstraints.insets = new Insets(0, 0, 4, 0);
    gbConstraints.gridwidth = 1;
    gbConstraints.gridx = 0;
    gbConstraints.weighty = 0;
    gbConstraints.weightx = 1;
    gbConstraints.fill = GridBagConstraints.BOTH;
    myCbSearchInComments = new NonFocusableCheckBox();
    myCbSearchInComments.setText(RefactoringBundle.getSearchInCommentsAndStringsText());
    myCbSearchInComments.setSelected(true);
    panel.add(myCbSearchInComments, gbConstraints);

    gbConstraints.insets = new Insets(0, 0, 4, 0);
    gbConstraints.gridwidth = GridBagConstraints.REMAINDER;
    gbConstraints.gridx = 1;
    gbConstraints.weightx = 1;
    gbConstraints.fill = GridBagConstraints.BOTH;
    myCbSearchTextOccurences = new NonFocusableCheckBox();
    myCbSearchTextOccurences.setText(RefactoringBundle.getSearchForTextOccurrencesText());
    myCbSearchTextOccurences.setSelected(true);
    panel.add(myCbSearchTextOccurences, gbConstraints);
    if (!TextOccurrencesUtil.isSearchTextOccurencesEnabled(myPsiElement)) {
      myCbSearchTextOccurences.setEnabled(false);
      myCbSearchTextOccurences.setSelected(false);
      myCbSearchTextOccurences.setVisible(false);
    }

    for(AutomaticRenamerFactory factory: Extensions.getExtensions(AutomaticRenamerFactory.EP_NAME)) {
      if (factory.isApplicable(myPsiElement) && factory.getOptionName() != null) {
        gbConstraints.insets = new Insets(0, 0, 4, 0);
        gbConstraints.gridwidth = myAutomaticRenamers.size() % 2 == 0 ? 1 : GridBagConstraints.REMAINDER;
        gbConstraints.gridx = myAutomaticRenamers.size() % 2;
        gbConstraints.weightx = 1;
        gbConstraints.fill = GridBagConstraints.BOTH;

        JCheckBox checkBox = new NonFocusableCheckBox();
        checkBox.setText(factory.getOptionName());
        checkBox.setSelected(factory.isEnabled());
        panel.add(checkBox, gbConstraints);
        myAutomaticRenamers.put(factory, checkBox);
      }
    }
  }

  @Override
  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(myHelpID);
  }

  @Override
  protected void doAction() {
    LOG.assertTrue(myPsiElement.isValid());

    final String newName = getNewName();
    performRename(newName);
  }

  public void performRename(final String newName) {
    final RenamePsiElementProcessor elementProcessor = RenamePsiElementProcessor.forElement(myPsiElement);
    elementProcessor.setToSearchInComments(myPsiElement, isSearchInComments());
    if (myCbSearchTextOccurences.isEnabled()) {
      elementProcessor.setToSearchForTextOccurrences(myPsiElement, isSearchInNonJavaFiles());
    }
    if (mySuggestedNameInfo != null) {
      mySuggestedNameInfo.nameChosen(newName);
    }

    final RenameProcessor processor = createRenameProcessor(newName);

    for(Map.Entry<AutomaticRenamerFactory, JCheckBox> e: myAutomaticRenamers.entrySet()) {
      e.getKey().setEnabled(e.getValue().isSelected());
      if (e.getValue().isSelected()) {
        processor.addRenamerFactory(e.getKey());
      }
    }

    invokeRefactoring(processor);
  }

  protected RenameProcessor createRenameProcessor(String newName) {
    return new RenameProcessor(getProject(), myPsiElement, newName, isSearchInComments(),
                                                          isSearchInNonJavaFiles());
  }

  @Override
  protected void canRun() throws ConfigurationException {
    if (Comparing.strEqual(getNewName(), myOldName)) throw new ConfigurationException(null);
    if (!areButtonsValid()) {
      throw new ConfigurationException("\'" + getNewName() + "\' is not a valid identifier");
    }
    final Function<String, String> inputValidator = RenameInputValidatorRegistry.getInputErrorValidator(myPsiElement);
    if (inputValidator != null) {
      setErrorText(inputValidator.fun(getNewName()));
    }
  }

  @Override
  protected boolean areButtonsValid() {
    final String newName = getNewName();
    return RenameUtil.isValidName(myProject, myPsiElement, newName);
  }

  protected NameSuggestionsField getNameSuggestionsField() {
    return myNameSuggestionsField;
  }

  public JCheckBox getCbSearchInComments() {
    return myCbSearchInComments;
  }
}
