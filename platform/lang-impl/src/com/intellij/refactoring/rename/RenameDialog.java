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

package com.intellij.refactoring.rename;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.rename.naming.AutomaticRenamerFactory;
import com.intellij.refactoring.ui.NameSuggestionsField;
import com.intellij.refactoring.ui.RefactoringDialog;
import com.intellij.refactoring.util.TextOccurrencesUtil;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.NonFocusableCheckBox;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.xml.util.XmlTagUtilBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.*;

public class RenameDialog extends RefactoringDialog {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.rename.RenameDialog");
  private SuggestedNameInfo mySuggestedNameInfo;

  private JLabel myNameLabel;
  private NameSuggestionsField myNameSuggestionsField;
  private JCheckBox myCbSearchInComments;
  private JCheckBox myCbSearchTextOccurences;
  private final JLabel myNewNamePrefix = new JLabel("");
  private final String myHelpID;
  private final PsiElement myPsiElement;
  private final PsiElement myNameSuggestionContext;
  private final Editor myEditor;
  private static final String REFACTORING_NAME = RefactoringBundle.message("rename.title");
  private NameSuggestionsField.DataChanged myNameChangedListener;
  private final Map<AutomaticRenamerFactory, JCheckBox> myAutomaticRenamers = new HashMap<AutomaticRenamerFactory, JCheckBox>();

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

    myNameLabel.setText("<html>" + XmlTagUtilBase.escapeString(RefactoringBundle.message("rename.0.and.its.usages.to", getFullName()), false) + "</html>");
    boolean toSearchInComments = isToSearchInCommentsForRename();
    myCbSearchInComments.setSelected(toSearchInComments);

    if (myCbSearchTextOccurences.isEnabled()) {
      boolean toSearchForTextOccurences = isToSearchForTextOccurencesForRename();
      myCbSearchTextOccurences.setSelected(toSearchForTextOccurences);
    }

    validateButtons();
    myHelpID = RenamePsiElementProcessor.forElement(psiElement).getHelpID(psiElement);
  }

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

  private String getFullName() {
    final String name = UsageViewUtil.getDescriptiveName(myPsiElement);
    return (UsageViewUtil.getType(myPsiElement) + " " + name).trim();
  }

  private void createNewNameComponent() {
    String[] suggestedNames = getSuggestedNames();
    myNameSuggestionsField = new NameSuggestionsField(suggestedNames, myProject, FileTypes.PLAIN_TEXT, myEditor);
    if (myPsiElement instanceof PsiFile && myEditor == null) {
      myNameSuggestionsField.selectNameWithoutExtension();
    }
    myNameChangedListener = new NameSuggestionsField.DataChanged() {
      public void dataChanged() {
        validateButtons();
      }
    };
    myNameSuggestionsField.addDataChangedListener(myNameChangedListener);

    myNameSuggestionsField.getComponent().registerKeyboardAction(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        completeVariable(myNameSuggestionsField.getEditor());
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, InputEvent.CTRL_MASK), JComponent.WHEN_IN_FOCUSED_WINDOW);
  }

  public String[] getSuggestedNames() {
    LinkedHashSet<String> result = new LinkedHashSet<String>();
    final NameSuggestionProvider[] providers = Extensions.getExtensions(NameSuggestionProvider.EP_NAME);
    for(NameSuggestionProvider provider: providers) {
      SuggestedNameInfo info = provider.getSuggestedNames(myPsiElement, myNameSuggestionContext, result);
      if (info != null) mySuggestedNameInfo = info;
    }
    if (result.size() == 0) {
      result.add(UsageViewUtil.getShortName(myPsiElement));
    }
    return ArrayUtil.toStringArray(result);
  }

  private void completeVariable(Editor editor) {
    final String prefix = myNameSuggestionsField.getEnteredName();
    Collection<LookupElement> items = null;
    for(NameSuggestionProvider provider: Extensions.getExtensions(NameSuggestionProvider.EP_NAME)) {
      items = provider.completeName(myPsiElement, myNameSuggestionContext, prefix);
      if (items != null) break;
    }

    if (items != null) {
      final LookupElement[] lookupItems = items.toArray(new LookupElement[items.size()]);
      editor.getCaretModel().moveToOffset(prefix.length());
      editor.getSelectionModel().removeSelection();
      LookupManager.getInstance(myProject).showLookup(editor, lookupItems, prefix);
    }
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

  public JComponent getPreferredFocusedComponent() {
    return myNameSuggestionsField.getFocusableComponent();
  }

  protected JComponent createCenterPanel() {
    return null;
  }

  protected JComponent createNorthPanel() {
    JPanel panel = new JPanel(new GridBagLayout());
    GridBagConstraints gbConstraints = new GridBagConstraints();

    panel.setBorder(IdeBorderFactory.createBorder());

    gbConstraints.insets = new Insets(4, 8, 4, 8);
    gbConstraints.weighty = 0;
    gbConstraints.weightx = 1;
    gbConstraints.gridwidth = GridBagConstraints.REMAINDER;
    gbConstraints.fill = GridBagConstraints.BOTH;
    myNameLabel = new JLabel();
    panel.add(myNameLabel, gbConstraints);

    gbConstraints.insets = new Insets(4, 8, 4, "".equals(myNewNamePrefix.getText()) ? 0 : 1);
    gbConstraints.gridwidth = 1;
    gbConstraints.fill = GridBagConstraints.NONE;
    gbConstraints.weightx = 0;
    gbConstraints.gridx = 0;
    gbConstraints.anchor = GridBagConstraints.WEST;
    panel.add(myNewNamePrefix, gbConstraints);

    gbConstraints.insets = new Insets(4, 8, 4, 8);
    gbConstraints.gridwidth = 2;
    gbConstraints.fill = GridBagConstraints.BOTH;
    gbConstraints.weightx = 1;
    gbConstraints.gridx = 0;
    gbConstraints.weighty = 1;
    panel.add(myNameSuggestionsField.getComponent(), gbConstraints);

    gbConstraints.insets = new Insets(4, 8, 4, 8);
    gbConstraints.gridwidth = 1;
    gbConstraints.gridx = 0;
    gbConstraints.weighty = 0;
    gbConstraints.weightx = 1;
    gbConstraints.fill = GridBagConstraints.BOTH;
    myCbSearchInComments = new NonFocusableCheckBox();
    myCbSearchInComments.setText(RefactoringBundle.getSearchInCommentsAndStringsText());
    myCbSearchInComments.setSelected(true);
    panel.add(myCbSearchInComments, gbConstraints);

    gbConstraints.insets = new Insets(4, 8, 4, 8);
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
        gbConstraints.insets = new Insets(4, 8, 4, 8);
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

    return panel;
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(myHelpID);
  }

  protected void doAction() {
    LOG.assertTrue(myPsiElement.isValid());

    final String newName = getNewName();
    performRename(newName);
  }

  @TestOnly
  public void performRename(final String newName) {
    final RenamePsiElementProcessor elementProcessor = RenamePsiElementProcessor.forElement(myPsiElement);
    elementProcessor.setToSearchInComments(myPsiElement, isSearchInComments());
    if (myCbSearchTextOccurences.isEnabled()) {
      elementProcessor.setToSearchForTextOccurrences(myPsiElement, isSearchInNonJavaFiles());
    }
    if (mySuggestedNameInfo != null) {
      mySuggestedNameInfo.nameChoosen(newName);
    }

    final RenameProcessor processor = new RenameProcessor(getProject(), myPsiElement, newName, isSearchInComments(),
                                                          isSearchInNonJavaFiles());

    for(Map.Entry<AutomaticRenamerFactory, JCheckBox> e: myAutomaticRenamers.entrySet()) {
      e.getKey().setEnabled(e.getValue().isSelected());
      if (e.getValue().isSelected()) {
        processor.addRenamerFactory(e.getKey());
      }
    }

    invokeRefactoring(processor);
  }

  @Override
  protected void canRun() throws ConfigurationException {
    if (!areButtonsValid()) {
      throw new ConfigurationException("\'" + StringUtil.first(getNewName(), 10, true) + "\' is invalid identifier");
    }
  }

  protected boolean areButtonsValid() {
    final String newName = getNewName();
    return RenameUtil.isValidName(myProject, myPsiElement, newName);
  }
}
