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

import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.CommonRefactoringUtil;

/**
 * @author ven
 */
public class InlineConstantFieldHandler extends JavaInlineActionHandler {
  private static final String REFACTORING_NAME = RefactoringBundle.message("inline.field.title");

  public boolean canInlineElement(PsiElement element) {
    return element instanceof PsiField;
  }

  public void inlineElement(Project project, Editor editor, PsiElement element) {
    PsiField field = (PsiField) element;
    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, field)) return;

    if (!field.hasModifierProperty(PsiModifier.FINAL)) {
      String message = RefactoringBundle.message("0.refactoring.is.supported.only.for.final.fields", REFACTORING_NAME);
      CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HelpID.INLINE_FIELD);
      return;
    }

    if (!field.hasInitializer()) {
      String message = RefactoringBundle.message("no.initializer.present.for.the.field");
      CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HelpID.INLINE_FIELD);
      return;
    }

    if (field instanceof PsiEnumConstant) {
      String message = REFACTORING_NAME + " is not supported for enum constants";
      CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HelpID.INLINE_FIELD);
      return;
    }

    if (ReferencesSearch.search(field, ProjectScope.getProjectScope(project), false).findFirst() == null) {
      String message = RefactoringBundle.message("field.0.is.never.used", field.getName());
      CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HelpID.INLINE_FIELD);
      return;
    }

    PsiReference reference = editor != null ? TargetElementUtilBase.findReference(editor, editor.getCaretModel().getOffset()) : null;
    if (reference != null && !field.equals(reference.resolve())) {
      reference = null;
    }

    final boolean invokedOnReference = (reference != null);
    if (!invokedOnReference) {
      if (!CommonRefactoringUtil.checkReadOnlyStatus(project, field)) return;
    }
    PsiReferenceExpression refExpression = reference instanceof PsiReferenceExpression ? (PsiReferenceExpression)reference : null;
    InlineFieldDialog dialog = new InlineFieldDialog(project, field, refExpression);
    dialog.show();
  }
}
