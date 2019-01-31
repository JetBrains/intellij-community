// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.inline;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiVariable;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.RefactoringBundle;

public class InlineLocalDialog extends AbstractInlineLocalDialog {
  public static final String REFACTORING_NAME = RefactoringBundle.message("inline.variable.title");

  private final PsiVariable myVariable;

  private int myOccurrencesNumber = -1;

  public InlineLocalDialog(Project project, PsiVariable variable, final PsiJavaCodeReferenceElement ref, int occurrencesCount) {
    super(project, variable, ref, occurrencesCount);
    myVariable = variable;
    myInvokedOnReference = ref != null;

    setTitle(REFACTORING_NAME);
    myOccurrencesNumber = occurrencesCount;
    init();
  }

  @Override
  protected String getNameLabelText() {
    return "Local variable " + myVariable.getName();
  }

  @Override
  protected String getBorderTitle() {
    return RefactoringBundle.message("inline.method.border.title");
  }

  @Override
  protected String getInlineThisText() {
    return RefactoringBundle.message("this.reference.only.and.keep.the.variable");
  }

  @Override
  protected String getInlineAllText() {
    final String occurrencesString = myOccurrencesNumber > -1 ? " (" + myOccurrencesNumber + " occurrence" + (myOccurrencesNumber == 1 ? ")" : "s)") : "";
    return RefactoringBundle.message("all.references.and.remove.the.local") + occurrencesString;
  }

  @Override
  protected void doAction() {
    JavaRefactoringSettings settings = JavaRefactoringSettings.getInstance();
    if(myRbInlineThisOnly.isEnabled() && myRbInlineAll.isEnabled()) {
      settings.INLINE_LOCAL_THIS = isInlineThisOnly();
    }
    close(OK_EXIT_CODE);
  }

  @Override
  protected String getHelpId() {
    return HelpID.INLINE_VARIABLE;
  }

  @Override
  protected boolean isInlineThis() {
    return JavaRefactoringSettings.getInstance().INLINE_LOCAL_THIS;
  }

  @Override
  protected boolean hasPreviewButton() {
    return false;
  }
}