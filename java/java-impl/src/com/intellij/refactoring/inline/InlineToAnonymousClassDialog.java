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
package com.intellij.refactoring.inline;

import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiCall;
import com.intellij.psi.PsiClass;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.RefactoringBundle;

import javax.swing.*;
import java.awt.*;

/**
 * @author yole
 */
public class InlineToAnonymousClassDialog extends InlineOptionsDialog {
  private final PsiClass myClass;
  private final PsiCall myCallToInline;
  private JCheckBox myCbSearchInComments;
  private JCheckBox myCbSearchTextOccurences;

  protected InlineToAnonymousClassDialog(Project project, PsiClass psiClass, final PsiCall callToInline, boolean isInvokeOnReference) {
    super(project, true, psiClass);
    myClass = psiClass;
    myCallToInline = callToInline;
    myInvokedOnReference = isInvokeOnReference;
    setTitle(RefactoringBundle.message("inline.to.anonymous.refactoring"));
    init();
  }

  protected String getNameLabelText() {
    String className = PsiFormatUtil.formatClass(myClass, PsiFormatUtil.SHOW_NAME);
    return RefactoringBundle.message("inline.to.anonymous.name.label", className);
  }

  protected String getBorderTitle() {
    return RefactoringBundle.message("inline.to.anonymous.border.title");
  }

  protected String getInlineAllText() {
    return RefactoringBundle.message("all.references.and.remove.the.class");
  }

  protected String getInlineThisText() {
    return RefactoringBundle.message("this.reference.only.and.keep.the.class");
  }

  protected boolean isInlineThis() {
    return false;
  }

  protected JComponent createCenterPanel() {
    JComponent optionsPanel = super.createCenterPanel();

    JPanel panel = new JPanel();
    panel.setLayout(new GridBagLayout());
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.weightx = 1.0;
    panel.add(optionsPanel, gbc);

    JavaRefactoringSettings settings = JavaRefactoringSettings.getInstance();
    myCbSearchInComments = new JCheckBox(RefactoringBundle.message("search.in.comments.and.strings"),
                                         settings.INLINE_CLASS_SEARCH_IN_COMMENTS);
    myCbSearchTextOccurences = new JCheckBox(RefactoringBundle.message("search.for.text.occurrences"),
                                             settings.INLINE_CLASS_SEARCH_IN_NON_JAVA);
    gbc.gridy = 1;
    panel.add(myCbSearchInComments, gbc);
    gbc.gridy = 2;
    panel.add(myCbSearchTextOccurences, gbc);
    return panel;
  }

  protected void doAction() {
    final boolean searchInComments = myCbSearchInComments.isSelected();
    final boolean searchInNonJava = myCbSearchTextOccurences.isSelected();

    JavaRefactoringSettings settings = JavaRefactoringSettings.getInstance();
    settings.INLINE_CLASS_SEARCH_IN_COMMENTS = searchInComments;
    settings.INLINE_CLASS_SEARCH_IN_NON_JAVA = searchInNonJava;

    invokeRefactoring(new InlineToAnonymousClassProcessor(getProject(), myClass, myCallToInline, isInlineThisOnly(),
                                                          searchInComments, searchInNonJava));
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(HelpID.INLINE_CLASS);
  }
}
