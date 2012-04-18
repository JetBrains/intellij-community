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
package com.intellij.psi.impl.source.resolve.reference.impl;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Konstantin Bulenkov
 */
public class JavaReflectionReferenceProvider extends PsiReferenceProvider {
  @NotNull
  @Override
  public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
    if (element instanceof PsiLiteralExpression) {
      String value = getValue(((PsiLiteralExpression)element));
      final PsiElement expressionList;
      if (value != null && (expressionList = element.getParent()) instanceof PsiExpressionList) {
        final PsiElement methodCall = expressionList.getParent();
        final PsiExpression classAccess;
        if (methodCall != null && (classAccess = getContext(methodCall)) != null) {
          return new PsiReference[]{new JavaLangClassMemberReference((PsiLiteralExpression)element, classAccess)};
        }
      }
    }
    return PsiReference.EMPTY_ARRAY;
  }

  @Nullable
  private static PsiExpression getContext(PsiElement methodCall) {
    final PsiClassObjectAccessExpression expression = PsiTreeUtil.findChildOfType(methodCall, PsiClassObjectAccessExpression.class);
    return expression == null ? PsiTreeUtil.findChildOfType(methodCall, PsiMethodCallExpression.class) : expression;
  }

  @Nullable
  private static String getValue(PsiLiteralExpression element) {
    final Object value = element.getValue();
    return value instanceof String ? (String)value : null;
  }
}
