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
import com.intellij.psi.*;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.RefactoringBundle;

public class InlineFieldDialog extends InlineOptionsWithSearchSettingsDialog {
  public static final String REFACTORING_NAME = RefactoringBundle.message("inline.field.title");
  private final PsiReferenceExpression myReferenceExpression;

  private final PsiField myField;
  protected final int myOccurrencesNumber;

  public InlineFieldDialog(Project project, PsiField field, PsiReferenceExpression ref) {
    super(project, true, field);
    myField = field;
    myReferenceExpression = ref;
    myInvokedOnReference = myReferenceExpression != null;

    setTitle(REFACTORING_NAME);
    myOccurrencesNumber = getNumberOfOccurrences(myField);
    init();
  }

  protected String getNameLabelText() {
    final String occurrencesString = myOccurrencesNumber > -1 ? "has " + myOccurrencesNumber + " occurrence" + (myOccurrencesNumber == 1 ? "" : "s") : "";

    String fieldText = PsiFormatUtil.formatVariable(myField, PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_TYPE,PsiSubstitutor.EMPTY);
    return RefactoringBundle.message("inline.field.field.name.label", fieldText, occurrencesString);
  }

  protected String getBorderTitle() {
    return RefactoringBundle.message("inline.field.border.title");
  }

  protected String getInlineThisText() {
    return RefactoringBundle.message("this.reference.only.and.keep.the.field");
  }

  protected String getInlineAllText() {
    return RefactoringBundle.message(myField.isWritable() ?"all.references.and.remove.the.field" : "all.invocations.in.project");
  }

  @Override
  protected String getKeepTheDeclarationText() {
    if (myField.isWritable()) return RefactoringBundle.message("all.references.keep.field");
    return super.getKeepTheDeclarationText();
  }

  @Override
  protected boolean allowInlineAll() {
    return true;
  }

  protected boolean isInlineThis() {
    return JavaRefactoringSettings.getInstance().INLINE_FIELD_THIS;
  }

  @Override
  protected boolean ignoreOccurrence(PsiReference reference) {
    return PsiTreeUtil.getParentOfType(reference.getElement(), PsiImportStatementBase.class) == null;
  }

  @Override
  protected boolean isSearchInCommentsAndStrings() {
    return JavaRefactoringSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_FIELD;
  }

  @Override
  protected void saveSearchInCommentsAndStrings(boolean searchInComments) {
    JavaRefactoringSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_FIELD = searchInComments;
  }

  @Override
  protected boolean isSearchForTextOccurrences() {
    return JavaRefactoringSettings.getInstance().RENAME_SEARCH_FOR_TEXT_FOR_FIELD;
  }

  @Override
  protected void saveSearchInTextOccurrences(boolean searchInTextOccurrences) {
    JavaRefactoringSettings.getInstance().RENAME_SEARCH_FOR_TEXT_FOR_FIELD = searchInTextOccurrences;
  }

  protected void doAction() {
    super.doAction();
    invokeRefactoring(
      new InlineConstantFieldProcessor(myField, getProject(), myReferenceExpression, isInlineThisOnly(), isSearchInCommentsAndStrings(),
                                       isSearchForTextOccurrences(), !isKeepTheDeclaration()));
    JavaRefactoringSettings settings = JavaRefactoringSettings.getInstance();
    if(myRbInlineThisOnly.isEnabled() && myRbInlineAll.isEnabled()) {
      settings.INLINE_FIELD_THIS = isInlineThisOnly();
    }
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(HelpID.INLINE_FIELD);
  }
}
