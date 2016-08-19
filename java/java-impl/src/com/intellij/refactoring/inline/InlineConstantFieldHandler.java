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

import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author ven
 */
public class InlineConstantFieldHandler extends JavaInlineActionHandler {
  private static final String REFACTORING_NAME = RefactoringBundle.message("inline.field.title");

  @Override
  public boolean canInlineElement(PsiElement element) {
    return element instanceof PsiField && StdLanguages.JAVA.equals(element.getLanguage());
  }

  @Override
  public void inlineElement(Project project, Editor editor, PsiElement element) {
    final PsiElement navigationElement = element.getNavigationElement();
    final PsiField field = (PsiField)(navigationElement instanceof PsiField ? navigationElement : element);

    if (getInitializer(field) == null) {
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

    if (!field.hasModifierProperty(PsiModifier.FINAL)) {
      final Ref<Boolean> hasWriteUsages = new Ref<>(false);
      if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> ApplicationManager.getApplication().runReadAction(() -> {
        for (PsiReference reference : ReferencesSearch.search(field)) {
          final PsiElement referenceElement = reference.getElement();
          if (!(referenceElement instanceof PsiExpression) || PsiUtil.isAccessedForWriting((PsiExpression)referenceElement)) {
            hasWriteUsages.set(true);
            break;
          }
        }
      }), "Check if Inline Is Possible...", true, project)) {
        return;
      }
      if (hasWriteUsages.get()) {
        String message = RefactoringBundle.message("0.refactoring.is.supported.only.for.final.fields", REFACTORING_NAME);
        CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HelpID.INLINE_FIELD);
        return;
      }
    }

    PsiReference reference = editor != null ? TargetElementUtil.findReference(editor, editor.getCaretModel().getOffset()) : null;
    if (reference != null) {
      final PsiElement resolve = reference.resolve();
      if (resolve != null && !field.equals(resolve.getNavigationElement())) {
        reference = null;
      }
    }

    if ((!(element instanceof PsiCompiledElement) || reference == null) && !CommonRefactoringUtil.checkReadOnlyStatus(project, field)) return;
    PsiReferenceExpression refExpression = reference instanceof PsiReferenceExpression ? (PsiReferenceExpression)reference : null;
    InlineFieldDialog dialog = new InlineFieldDialog(project, field, refExpression);
    dialog.show();
  }

  @Nullable
  protected static PsiExpression getInitializer(PsiField field) {
    if (field.hasInitializer()) {
      return field.getInitializer();
    }

    if (field.hasModifierProperty(PsiModifier.FINAL)) {
      PsiClass containingClass = field.getContainingClass();
      if (containingClass != null) {
        PsiMethod[] constructors = containingClass.getConstructors();
        final List<PsiExpression> result = new ArrayList<>();
        for (PsiReference reference : ReferencesSearch.search(field, new LocalSearchScope(constructors))) {
          final PsiElement element = reference.getElement();
          if (element instanceof PsiReferenceExpression && PsiUtil.isOnAssignmentLeftHand((PsiExpression)element)) {
            PsiAssignmentExpression assignmentExpression = PsiTreeUtil.getParentOfType(element, PsiAssignmentExpression.class);
            if (assignmentExpression != null) {
              ContainerUtil.addIfNotNull(result, assignmentExpression.getRExpression());
            }
          }
        }

        if (result.isEmpty()) return null;

        PsiExpression first = result.get(0);
        for (PsiExpression expr : result) {
          if (!PsiEquivalenceUtil.areElementsEquivalent(expr, first)) {
            return null;
          }
        }
        return first;
      }
    }

    return null;
  }
}
