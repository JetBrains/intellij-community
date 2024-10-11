// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

public class InlineFieldDialog extends InlineOptionsWithSearchSettingsDialog {
  private final PsiElement myReferenceExpression;

  private final PsiField myField;
  protected final int myOccurrencesNumber;

  public InlineFieldDialog(Project project, PsiField field, PsiElement ref) {
    super(project, true, field);
    myField = field;
    myReferenceExpression = ref;
    myOccurrencesNumber = getNumberOfOccurrences(myField);
    myInvokedOnReference = myReferenceExpression != null && myOccurrencesNumber != 1;

    setTitle(getRefactoringName());
    init();
  }

  @Override
  protected String getNameLabelText() {
    int options = myReferenceExpression != null
                  ? PsiFormatUtilBase.SHOW_CONTAINING_CLASS | PsiFormatUtilBase.SHOW_NAME
                  : PsiFormatUtilBase.SHOW_NAME;
    String fieldText = PsiFormatUtil.formatVariable(myField, options, PsiSubstitutor.EMPTY);
    return myOccurrencesNumber > -1 ?
           JavaRefactoringBundle.message("inline.field.field.occurrences", fieldText, myOccurrencesNumber) :
           JavaRefactoringBundle.message("inline.field.field.name.label", fieldText);
  }

  @Override
  protected String getInlineThisText() {
    return JavaRefactoringBundle.message("this.reference.only.and.keep.the.field");
  }

  @Override
  protected String getInlineAllText() {
    return JavaRefactoringBundle.message(isLibraryInline() ? "all.invocations.in.project" : "all.references.and.remove.the.field");
  }

  @Override
  protected String getKeepTheDeclarationText() {
    if (!isLibraryInline()) return JavaRefactoringBundle.message("all.references.keep.field");
    return super.getKeepTheDeclarationText();
  }

  private boolean isLibraryInline() {
    return myField.getOriginalElement() instanceof PsiCompiledElement;
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
                                       isSearchForTextOccurrences(), !isLibraryInline() && !isKeepTheDeclaration()));
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