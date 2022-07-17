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

package com.intellij.refactoring.replaceConstructorWithBuilder;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ReplaceConstructorWithBuilderHandler implements RefactoringActionHandler {
  @Override
  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file, final DataContext dataContext) {
    final int offset = editor.getCaretModel().getOffset();
    final PsiElement element = file.findElementAt(offset);
    final PsiClass psiClass = getParentNamedClass(element);
    if (psiClass == null) {
      showErrorMessage(JavaRefactoringBundle.message("replace.constructor.builder.error.caret.position"), project, editor);
      return;
    }

    final PsiMethod[] constructors = psiClass.getConstructors();
    if (constructors.length == 0) {
      showErrorMessage(JavaRefactoringBundle.message("replace.constructor.builder.error.no.constructors"), project, editor);
      return;
    }

    new ReplaceConstructorWithBuilderDialog(project, constructors).show();
  }

  @Nullable
  public static PsiClass getParentNamedClass(PsiElement element) {
    if (element != null) {
      final PsiElement parent = element.getParent();
      if (parent instanceof PsiJavaCodeReferenceElement) {
        final PsiElement resolve = ((PsiJavaCodeReferenceElement)parent).resolve();
        if (resolve instanceof PsiClass) return (PsiClass)resolve;
      }
    }
    final PsiClass psiClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
    if (psiClass instanceof PsiAnonymousClass) {
      return getParentNamedClass(psiClass);
    }
    return psiClass;
  }

  @Override
  public void invoke(@NotNull final Project project, final PsiElement @NotNull [] elements, final DataContext dataContext) {
    throw new UnsupportedOperationException();
  }

  private static void showErrorMessage(@NlsContexts.DialogMessage String message, Project project, Editor editor) {
    CommonRefactoringUtil.showErrorHint(project, editor, message, JavaRefactoringBundle.message("replace.constructor.with.builder"), HelpID.REPLACE_CONSTRUCTOR_WITH_BUILDER);
  }
}
