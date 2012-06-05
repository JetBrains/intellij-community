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

/**
 * @author yole
 */
public class InlineToAnonymousClassDialog extends InlineOptionsWithSearchSettingsDialog {
  private final PsiClass myClass;
  private final PsiCall myCallToInline;

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

  @Override
  protected boolean isSearchInCommentsAndStrings() {
    return JavaRefactoringSettings.getInstance().INLINE_CLASS_SEARCH_IN_COMMENTS;
  }

  @Override
  protected boolean isSearchForTextOccurrences() {
    return JavaRefactoringSettings.getInstance().INLINE_CLASS_SEARCH_IN_NON_JAVA;
  }

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
  protected void saveSearchInTextOccurrences(boolean searchInTextOccurrences) {
    JavaRefactoringSettings.getInstance().INLINE_CLASS_SEARCH_IN_NON_JAVA = searchInTextOccurrences;
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(HelpID.INLINE_CLASS);
  }
}
