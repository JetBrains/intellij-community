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
package com.intellij.refactoring.invertBoolean;

import com.intellij.lang.LanguageNamesValidation;
import com.intellij.lang.findUsages.DescriptiveNameUtil;
import com.intellij.lang.refactoring.NamesValidator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.help.HelpManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.ui.RefactoringDialog;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.usageView.UsageViewUtil;

import javax.swing.*;

/**
 * @author ven
 */
public class InvertBooleanDialog extends RefactoringDialog {
  private JTextField myNameField;
  private JPanel myPanel;
  private JLabel myLabel;
  private JLabel myCaptionLabel;

  private final PsiElement myElement;

  public InvertBooleanDialog(final PsiElement element) {
    super(element.getProject(), false);
    myElement = element;
    final String name = myElement instanceof PsiNamedElement ? ((PsiNamedElement)myElement).getName() : myElement.getText();
    myNameField.setText(name);
    myLabel.setLabelFor(myNameField);
    final String typeString = UsageViewUtil.getType(myElement);
    myLabel.setText(RefactoringBundle.message("invert.boolean.name.of.inverted.element", typeString));
    myCaptionLabel.setText(RefactoringBundle.message("invert.0.1",
                                                     typeString,
                                                     DescriptiveNameUtil.getDescriptiveName(myElement)));

    setTitle(InvertBooleanHandler.REFACTORING_NAME);
    init();
  }

  public JComponent getPreferredFocusedComponent() {
    return myNameField;
  }

  protected void doAction() {
    Project project = myElement.getProject();
    final String name = myNameField.getText().trim();
    final NamesValidator namesValidator = LanguageNamesValidation.INSTANCE.forLanguage(myElement.getLanguage());
    if (namesValidator != null && !namesValidator.isIdentifier(name, myProject)) {
      CommonRefactoringUtil.showErrorMessage(InvertBooleanHandler.REFACTORING_NAME,
                                             RefactoringBundle.message("please.enter.a.valid.name.for.inverted.element",
                                                                       UsageViewUtil.getType(myElement)),
                                             InvertBooleanHandler.INVERT_BOOLEAN_HELP_ID, project);
      return;
    }

    invokeRefactoring(new InvertBooleanProcessor(myElement, name));
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(InvertBooleanHandler.INVERT_BOOLEAN_HELP_ID);
  }

  protected JComponent createCenterPanel() {
    return myPanel;
  }
}
