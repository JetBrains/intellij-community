/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
    return "Inline this occurrence and leave the variable";
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
  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(HelpID.INLINE_VARIABLE);
  }

  @Override
  protected boolean isInlineThis() {
    return JavaRefactoringSettings.getInstance().INLINE_LOCAL_THIS;
  }
}
