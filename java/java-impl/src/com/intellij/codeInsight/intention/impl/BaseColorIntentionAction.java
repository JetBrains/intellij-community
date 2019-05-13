/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

/**
 * @author Danila Ponomarenko
 * @author Konstantin Bulenkov
 */
public abstract class BaseColorIntentionAction extends PsiElementBaseIntentionAction implements HighPriorityAction {
  protected static final String JAVA_AWT_COLOR = "java.awt.Color";
  protected static final String COLOR_UI_RESOURCE = "javax.swing.plaf.ColorUIResource";

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    final PsiNewExpression expression = PsiTreeUtil.getParentOfType(element, PsiNewExpression.class, false, PsiMember.class, PsiCodeBlock.class);
    return expression != null
           && isJavaAwtColor(expression.getClassOrAnonymousClassReference())
           && isValueArguments(expression.getArgumentList());
  }

  private static boolean isJavaAwtColor(@Nullable PsiJavaCodeReferenceElement ref) {
    final String fqn = getFqn(ref);
    return JAVA_AWT_COLOR.equals(fqn) || COLOR_UI_RESOURCE.equals(fqn);
  }

  @Nullable
  protected static String getFqn(@Nullable PsiJavaCodeReferenceElement ref) {
    if (ref != null) {
      final PsiReference reference = ref.getReference();
      if (reference != null) {
        final PsiElement psiElement = reference.resolve();
        if (psiElement instanceof PsiClass) {
          return ((PsiClass)psiElement).getQualifiedName();
        }
      }
    }
    return null;
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
