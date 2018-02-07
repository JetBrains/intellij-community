/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.RefactoringBundle;

public class InlineMethodDialog extends InlineOptionsWithSearchSettingsDialog {
  public static final String REFACTORING_NAME = RefactoringBundle.message("inline.method.title");
  private final PsiJavaCodeReferenceElement myReferenceElement;
  private final Editor myEditor;
  private final boolean myAllowInlineThisOnly;

  private final PsiMethod myMethod;

  private int myOccurrencesNumber = -1;

  public InlineMethodDialog(Project project, PsiMethod method, PsiJavaCodeReferenceElement ref, Editor editor,
                            final boolean allowInlineThisOnly) {
    super(project, true, method);
    myMethod = method;
    myReferenceElement = ref;
    myEditor = editor;
    myAllowInlineThisOnly = allowInlineThisOnly;
    myInvokedOnReference = ref != null;

    setTitle(REFACTORING_NAME);
    myOccurrencesNumber = getNumberOfOccurrences(method);
    init();
  }

  @Override
  protected boolean allowInlineAll() {
    return true;
  }

  @Override
  protected String getNameLabelText() {
    final String occurrencesString = myOccurrencesNumber > -1 ? "has " + myOccurrencesNumber + " occurrence" + (myOccurrencesNumber == 1 ? "" : "s") : "";
    String methodText = PsiFormatUtil.formatMethod(myMethod,
                                                   PsiSubstitutor.EMPTY, PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_PARAMETERS,
                                                   PsiFormatUtilBase.SHOW_TYPE);
    return RefactoringBundle.message("inline.method.method.label", methodText, occurrencesString);
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
    if (myMethod.isWritable()) return RefactoringBundle.message("all.invocations.keep.the.method");
    return super.getKeepTheDeclarationText();
  }

  @Override
  protected void doAction() {
    super.doAction();
    invokeRefactoring(
      new InlineMethodProcessor(getProject(), myMethod, myReferenceElement, myEditor, isInlineThisOnly(), isSearchInCommentsAndStrings(),
                                isSearchForTextOccurrences(), !isKeepTheDeclaration()));
    JavaRefactoringSettings settings = JavaRefactoringSettings.getInstance();
    if(myRbInlineThisOnly.isEnabled() && myRbInlineAll.isEnabled()) {
      settings.INLINE_METHOD_THIS = isInlineThisOnly();
    }
  }

  @Override
  protected void doHelpAction() {
    if (myMethod.isConstructor()) HelpManager.getInstance().invokeHelp(HelpID.INLINE_CONSTRUCTOR);
    else HelpManager.getInstance().invokeHelp(HelpID.INLINE_METHOD);
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
}
