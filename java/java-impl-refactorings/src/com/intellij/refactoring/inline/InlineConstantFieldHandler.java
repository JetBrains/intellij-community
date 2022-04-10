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
import com.intellij.codeInsight.highlighting.ReadWriteAccessDetector;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.InlineUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author ven
 */
public class InlineConstantFieldHandler extends JavaInlineActionHandler {

  @Override
  public boolean canInlineElement(PsiElement element) {
    return element instanceof PsiField && JavaLanguage.INSTANCE.equals(element.getLanguage());
  }

  @Override
  public void inlineElement(Project project, Editor editor, PsiElement element) {
    final PsiElement navigationElement = element.getNavigationElement();
    final PsiField field = (PsiField)(navigationElement instanceof PsiField ? navigationElement : element);

    PsiExpression initializer = getInitializer(field);
    if (initializer == null) {
      String message = JavaRefactoringBundle.message("no.initializer.present.for.the.field");
      CommonRefactoringUtil.showErrorHint(project, editor, message, getRefactoringName(), HelpID.INLINE_FIELD);
      return;
    }

    if (field instanceof PsiEnumConstant) {
      String message = JavaRefactoringBundle.message("inline.constant.field.not.supported.for.enum.constants", getRefactoringName());
      CommonRefactoringUtil.showErrorHint(project, editor, message, getRefactoringName(), HelpID.INLINE_FIELD);
      return;
    }

    if (ReferencesSearch.search(field, ProjectScope.getProjectScope(project), false).findFirst() == null) {
      String message = JavaRefactoringBundle.message("field.0.is.never.used", field.getName());
      CommonRefactoringUtil.showErrorHint(project, editor, message, getRefactoringName(), HelpID.INLINE_FIELD);
      return;
    }

    if (!field.hasModifierProperty(PsiModifier.FINAL)) {
      final Ref<Boolean> hasWriteUsages = new Ref<>(false);
      if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> ApplicationManager.getApplication().runReadAction(() -> {
        for (PsiReference reference : ReferencesSearch.search(field)) {
          if (isAccessedForWriting(reference.getElement())) {
            hasWriteUsages.set(true);
            break;
          }
        }
      }), JavaRefactoringBundle.message("inline.conflicts.progress"), true, project)) {
        return;
      }
      if (hasWriteUsages.get()) {
        String message = JavaRefactoringBundle.message("0.refactoring.is.supported.only.for.final.fields", getRefactoringName());
        CommonRefactoringUtil.showErrorHint(project, editor, message, getRefactoringName(), HelpID.INLINE_FIELD);
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

    MultiMap<PsiElement, String> conflicts = new MultiMap<>();
    InlineUtil.checkChangedBeforeLastAccessConflicts(conflicts, initializer, field);

    if (!BaseRefactoringProcessor.processConflicts(project, conflicts)) return;

    PsiElement referenceElement = reference != null ? reference.getElement() : null;
    if (referenceElement != null && 
        referenceElement.getLanguage() == JavaLanguage.INSTANCE &&
        !(referenceElement instanceof PsiReferenceExpression)) {
      referenceElement = null; 
    }
    InlineFieldDialog dialog = new InlineFieldDialog(project, field, referenceElement);
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      try {
        dialog.doAction();
      } finally {
        dialog.close(DialogWrapper.OK_EXIT_CODE, true);
      }
    }
    else {
      dialog.show();
    }
  }

  private static boolean isAccessedForWriting(PsiElement referenceElement) {
    if (referenceElement.getLanguage() == JavaLanguage.INSTANCE) {
      if (!(referenceElement instanceof PsiExpression) || PsiUtil.isAccessedForWriting((PsiExpression)referenceElement)) {
        return true;
      }
    }
    else {
      for (ReadWriteAccessDetector detector : ReadWriteAccessDetector.EP_NAME.getExtensionList()) {
        if (detector.getExpressionAccess(referenceElement) != ReadWriteAccessDetector.Access.Read) {
          return true;
        }
      }
    }
    return false;
  }
  
  @Nullable
  public static PsiExpression getInitializer(PsiField field) {
    if (field.hasInitializer()) {
      PsiExpression initializer = field.getInitializer();
      if (initializer instanceof PsiCompiledElement) {
        // Could be a literal initializer: we still can inline it, though passing compiled element downstream may cause exceptions
        initializer = JavaPsiFacade.getElementFactory(field.getProject()).createExpressionFromText(initializer.getText(), field);
      }
      return initializer;
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

  @Nullable
  @Override
  public String getActionName(PsiElement element) {
    return JavaRefactoringBundle.message("inline.field.action.name");
  }

  private static @NlsActions.ActionText String getRefactoringName() {
    return JavaRefactoringBundle.message("inline.field.title");
  }
}
