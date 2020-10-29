// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.inline;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.RefactoringBundle;

public class InlineFieldDialog extends InlineOptionsWithSearchSettingsDialog {
  private final PsiElement myReferenceExpression;

  private final PsiField myField;
  protected final int myOccurrencesNumber;

  public InlineFieldDialog(Project project, PsiField field, PsiElement ref) {
    super(project, true, field);
    myField = field;
    myReferenceExpression = ref;
    myInvokedOnReference = myReferenceExpression != null;

    setTitle(getRefactoringName());
    myOccurrencesNumber = getNumberOfOccurrences(myField);
    init();
  }

  @Override
  protected String getNameLabelText() {
    String fieldText = PsiFormatUtil.formatVariable(myField, PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_TYPE, PsiSubstitutor.EMPTY);
    String occurrencesString = myOccurrencesNumber > -1 ?
                              JavaRefactoringBundle.message("inline.field.field.occurrences", fieldText, myOccurrencesNumber) :
                              JavaRefactoringBundle.message("inline.field.field.name.label", fieldText);
    return JavaRefactoringBundle.message("inline.field.field.name.label", fieldText, occurrencesString);
  }

  @Override
  protected String getBorderTitle() {
    return RefactoringBundle.message("inline.field.border.title");
  }

  @Override
  protected String getInlineThisText() {
    return JavaRefactoringBundle.message("this.reference.only.and.keep.the.field");
  }

  @Override
  protected String getInlineAllText() {
    return myField.isWritable()
           ? JavaRefactoringBundle.message("all.references.and.remove.the.field")
           : RefactoringBundle.message("all.invocations.in.project");
  }

  @Override
  protected String getKeepTheDeclarationText() {
    if (myField.isWritable()) return JavaRefactoringBundle.message("all.references.keep.field");
    return super.getKeepTheDeclarationText();
  }

  @Override
  protected boolean allowInlineAll() {
    return true;
  }

  @Override
  protected boolean isInlineThis() {
    return JavaRefactoringSettings.getInstance().INLINE_FIELD_THIS;
  }

  @Override
  protected boolean isKeepTheDeclarationByDefault() {
    return JavaRefactoringSettings.getInstance().INLINE_FIELD_KEEP;
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

  @Override
  protected void doAction() {
    super.doAction();
    invokeRefactoring(
      new InlineConstantFieldProcessor(myField, getProject(), myReferenceExpression, isInlineThisOnly(), isSearchInCommentsAndStrings(),
                                       isSearchForTextOccurrences(), !isKeepTheDeclaration()));
    JavaRefactoringSettings settings = JavaRefactoringSettings.getInstance();
    if(myRbInlineThisOnly.isEnabled() && myRbInlineAll.isEnabled()) {
      settings.INLINE_FIELD_THIS = isInlineThisOnly();
    }
    if (myKeepTheDeclaration != null && myKeepTheDeclaration.isEnabled()) {
      settings.INLINE_FIELD_KEEP = isKeepTheDeclaration();
    } 
  }

  @Override
  protected String getHelpId() {
    return HelpID.INLINE_FIELD;
  }

  public static @NlsContexts.DialogTitle String getRefactoringName() {
    return JavaRefactoringBundle.message("inline.field.title");
  }
}