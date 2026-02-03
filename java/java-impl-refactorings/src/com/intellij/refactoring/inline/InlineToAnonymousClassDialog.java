// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.inline;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiCall;
import com.intellij.psi.PsiClass;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.JavaRefactoringSettings;


public class InlineToAnonymousClassDialog extends InlineOptionsWithSearchSettingsDialog {
  private final PsiClass myClass;
  private final PsiCall myCallToInline;

  protected InlineToAnonymousClassDialog(Project project, PsiClass psiClass, final PsiCall callToInline, boolean isInvokeOnReference) {
    super(project, true, psiClass);
    myClass = psiClass;
    myCallToInline = callToInline;
    myInvokedOnReference = isInvokeOnReference;
    setTitle(JavaRefactoringBundle.message("inline.to.anonymous.refactoring"));
    init();
  }

  @Override
  protected String getNameLabelText() {
    String className = PsiFormatUtil.formatClass(myClass, PsiFormatUtilBase.SHOW_NAME);
    return JavaRefactoringBundle.message("inline.to.anonymous.name.label", className);
  }

  @Override
  protected String getBorderTitle() {
    return JavaRefactoringBundle.message("inline.to.anonymous.border.title");
  }

  @Override
  protected String getInlineAllText() {
    return JavaRefactoringBundle.message("all.references.and.remove.the.class");
  }

  @Override
  protected String getInlineThisText() {
    return JavaRefactoringBundle.message("this.reference.only.and.keep.the.class");
  }

  @Override
  protected boolean isInlineThis() {
    return false;
  }

  @Override
  protected boolean isSearchInCommentsAndStrings() {
    return JavaRefactoringSettings.getInstance().INLINE_CLASS_SEARCH_IN_COMMENTS;
  }

  @Override
  protected boolean isSearchForTextOccurrences() {
    return JavaRefactoringSettings.getInstance().INLINE_CLASS_SEARCH_IN_NON_JAVA;
  }

  @Override
  protected void doAction() {
    super.doAction();
    invokeRefactoring(new InlineToAnonymousClassProcessor(getProject(), myClass, myCallToInline, isInlineThisOnly(),
                                                          isSearchInCommentsAndStrings(), isSearchForTextOccurrences()));
  }

  @Override
  protected void saveSearchInCommentsAndStrings(boolean searchInComments) {
    JavaRefactoringSettings.getInstance().INLINE_CLASS_SEARCH_IN_COMMENTS = searchInComments;
  }

  @Override
  protected boolean allowInlineAll() {
    return true;
  }

  @Override
  protected void saveSearchInTextOccurrences(boolean searchInTextOccurrences) {
    JavaRefactoringSettings.getInstance().INLINE_CLASS_SEARCH_IN_NON_JAVA = searchInTextOccurrences;
  }

  @Override
  protected String getHelpId() {
    return HelpID.INLINE_CLASS;
  }
}