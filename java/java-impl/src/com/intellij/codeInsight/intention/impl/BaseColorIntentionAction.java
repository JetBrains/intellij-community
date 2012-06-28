/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.patterns.PlatformPatterns.psiElement;

/**
 * @author Danila Ponomarenko
 */
public abstract class BaseColorIntentionAction extends PsiElementBaseIntentionAction implements HighPriorityAction {
  protected static final String JAVA_AWT_COLOR = "java.awt.Color";

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    if (!psiElement().inside(psiElement(PsiNewExpression.class)).accepts(element)) {
      return false;
    }

    final PsiNewExpression expression = PsiTreeUtil.getParentOfType(element, PsiNewExpression.class, false);
    if (expression == null) {
      return false;
    }

    return isJavaAwtColor(expression.getClassOrAnonymousClassReference()) && isValueArguments(expression.getArgumentList());
  }

  private static boolean isJavaAwtColor(@Nullable PsiJavaCodeReferenceElement ref) {
    if (ref == null) {
      return false;
    }

    final PsiReference reference = ref.getReference();
    if (reference == null) {
      return false;
    }

    final PsiElement psiElement = reference.resolve();
    if (psiElement instanceof PsiClass && JAVA_AWT_COLOR.equals(((PsiClass)psiElement).getQualifiedName())) {
      return true;
    }

    return false;
  }

  private static boolean isValueArguments(@Nullable PsiExpressionList arguments) {
    if (arguments == null) {
      return false;
    }

    for (PsiExpression argument : arguments.getExpressions()) {
      if (argument instanceof PsiReferenceExpression) {
        return false;
      }
    }

    return true;
  }
}
