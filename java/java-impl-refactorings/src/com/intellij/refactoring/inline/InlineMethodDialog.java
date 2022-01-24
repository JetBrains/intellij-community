// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.inline;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.RefactoringBundle;

public class InlineMethodDialog extends InlineOptionsWithSearchSettingsDialog {
  private final PsiReference myReference;
  private final Editor myEditor;
  private final boolean myAllowInlineThisOnly;
  private final PsiMethod myMethod;
  private final int myOccurrencesNumber;

  public InlineMethodDialog(Project project, PsiMethod method, PsiReference ref, Editor editor, boolean allowInlineThisOnly) {
    super(project, true, method);
    myMethod = method;
    myReference = ref;
    myEditor = editor;
    myAllowInlineThisOnly = allowInlineThisOnly;
    myInvokedOnReference = ref != null;
    myOccurrencesNumber = getNumberOfOccurrences(method);

    setTitle(getRefactoringName());
    init();
  }

  @Override
  protected boolean allowInlineAll() {
    return true;
  }

  @Override
  protected String getNameLabelText() {
    String methodText = PsiFormatUtil.formatMethod(myMethod,
                                                   PsiSubstitutor.EMPTY, PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_PARAMETERS,
                                                   PsiFormatUtilBase.SHOW_TYPE);
    return myOccurrencesNumber > -1 ?
           JavaRefactoringBundle.message("inline.method.method.occurrences", methodText, myOccurrencesNumber) :
           JavaRefactoringBundle.message("inline.method.method.label", methodText);
  }

  @Override
  protected String getBorderTitle() {
    return RefactoringBundle.message("inline.method.border.title");
  }

  @Override
  protected String getInlineThisText() {
    return RefactoringBundle.message("this.invocation.only.and.keep.the.method");
  }

  @Override
  protected String getInlineAllText() {
    return RefactoringBundle.message(myMethod.isWritable() ? "all.invocations.and.remove.the.method" : "all.invocations.in.project");
  }

  @Override
  protected String getKeepTheDeclarationText() {
    if (myMethod.isWritable()) return JavaRefactoringBundle.message("all.invocations.keep.the.method");
    return super.getKeepTheDeclarationText();
  }

  @Override
  protected void doAction() {
    super.doAction();
    invokeRefactoring(
      new InlineMethodProcessor(getProject(), myMethod, myReference, myEditor, isInlineThisOnly(), isSearchInCommentsAndStrings(),
                                isSearchForTextOccurrences(), !isKeepTheDeclaration()));
    JavaRefactoringSettings settings = JavaRefactoringSettings.getInstance();
    if(myRbInlineThisOnly.isEnabled() && myRbInlineAll.isEnabled()) {
      settings.INLINE_METHOD_THIS = isInlineThisOnly();
    }

    if (myKeepTheDeclaration != null && myKeepTheDeclaration.isEnabled()) {
      settings.INLINE_METHOD_KEEP = isKeepTheDeclaration();
    }
  }

  @Override
  protected String getHelpId() {
    return myMethod.isConstructor() ? HelpID.INLINE_CONSTRUCTOR : HelpID.INLINE_METHOD;
  }

  @Override
  protected boolean canInlineThisOnly() {
    return InlineMethodHandler.checkRecursive(myMethod) || myAllowInlineThisOnly;
  }

  @Override
  protected boolean ignoreOccurrence(PsiReference reference) {
    return PsiTreeUtil.getParentOfType(reference.getElement(), PsiImportStatementBase.class) == null;
  }

  @Override
  protected boolean isInlineThis() {
    return JavaRefactoringSettings.getInstance().INLINE_METHOD_THIS;
  }

  @Override
  protected boolean isKeepTheDeclarationByDefault() {
    return JavaRefactoringSettings.getInstance().INLINE_METHOD_KEEP;
  }

  @Override
  protected boolean isSearchInCommentsAndStrings() {
    return JavaRefactoringSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_METHOD;
  }

  @Override
  protected void saveSearchInCommentsAndStrings(boolean searchInComments) {
    JavaRefactoringSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_METHOD = searchInComments;
  }

  @Override
  protected boolean isSearchForTextOccurrences() {
    return JavaRefactoringSettings.getInstance().RENAME_SEARCH_FOR_TEXT_FOR_METHOD;
  }

  @Override
  protected void saveSearchInTextOccurrences(boolean searchInTextOccurrences) {
    JavaRefactoringSettings.getInstance().RENAME_SEARCH_FOR_TEXT_FOR_METHOD = searchInTextOccurrences;
  }

  public static @NlsContexts.DialogTitle String getRefactoringName() {
    return RefactoringBundle.message("inline.method.title");
  }

  @Override
  protected int getNumberOfOccurrences(PsiNameIdentifierOwner nameIdentifierOwner) {
    return getNumberOfOccurrences(nameIdentifierOwner, 
                                  this::ignoreOccurrence, 
                                  scope -> MethodReferencesSearch.search((PsiMethod)nameIdentifierOwner, scope, true));
  }
}