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
package com.intellij.refactoring.psi;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class MutationUtils {
  private MutationUtils() { }

  public static void replaceType(@NotNull String newTypeText, @NotNull PsiTypeElement typeElement) throws IncorrectOperationException {
    final Project project = typeElement.getProject();
    final PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
    final PsiType newType = factory.createTypeFromText(newTypeText, null);
    final PsiTypeElement newTypeElement = factory.createTypeElement(newType);
    final PsiElement insertedElement = typeElement.replace(newTypeElement);
    final PsiElement shortenedElement = JavaCodeStyleManager.getInstance(project).shortenClassReferences(insertedElement);
    CodeStyleManager.getInstance(project).reformat(shortenedElement);
  }

  public static void replaceExpression(@NotNull String newExpression, @NotNull PsiExpression exp) throws IncorrectOperationException {
    final Project project = exp.getProject();
    final PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
    final PsiExpression newCall = factory.createExpressionFromText(newExpression, null);
    final PsiElement insertedElement = exp.replace(newCall);
    final PsiElement shortenedElement = JavaCodeStyleManager.getInstance(project).shortenClassReferences(insertedElement);
    CodeStyleManager.getInstance(project).reformat(shortenedElement);
  }

  public static void replaceExpressionIfValid(@NotNull String newExpression, @NotNull PsiExpression exp) throws IncorrectOperationException {
    try {
      replaceExpression(newExpression, exp);
    }
    catch (IncorrectOperationException ignored) { }
  }

  public static void replaceReference(@NotNull String className, @NotNull PsiJavaCodeReferenceElement ref) throws IncorrectOperationException {
    final Project project = ref.getProject();
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    final PsiElementFactory factory = facade.getElementFactory();
    final GlobalSearchScope scope = GlobalSearchScope.allScope(project);

    final PsiElement insertedElement;
    final PsiElement parent = ref.getParent();
    if (parent instanceof PsiReferenceExpression) {
      final PsiClass aClass = facade.findClass(className, scope);
      if (aClass == null) return;
      ((PsiReferenceExpression)parent).setQualifierExpression(factory.createReferenceExpression(aClass));
      insertedElement = ((PsiReferenceExpression)parent).getQualifierExpression();
      assert insertedElement != null : parent;
    }
    else {
      final PsiJavaCodeReferenceElement newReference = factory.createReferenceElementByFQClassName(className, scope);
      insertedElement = ref.replace(newReference);
    }
    final PsiElement shortenedElement = JavaCodeStyleManager.getInstance(project).shortenClassReferences(insertedElement);
    CodeStyleManager.getInstance(project).reformat(shortenedElement);
  }

  public static void replaceStatement(@NotNull String newStatement, @NotNull PsiStatement statement) throws IncorrectOperationException {
    final Project project = statement.getProject();
    final PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
    final PsiStatement newCall = factory.createStatementFromText(newStatement, null);
    final PsiElement insertedElement = statement.replace(newCall);
    final PsiElement shortenedElement = JavaCodeStyleManager.getInstance(project).shortenClassReferences(insertedElement);
    CodeStyleManager.getInstance(project).reformat(shortenedElement);
  }
}
