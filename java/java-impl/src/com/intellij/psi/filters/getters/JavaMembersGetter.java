/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.psi.filters.getters;

import com.intellij.codeInsight.completion.JavaCompletionUtil;
import com.intellij.codeInsight.completion.JavaMethodCallElement;
import com.intellij.codeInsight.completion.SmartCompletionDecorator;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.VariableLookupItem;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public class JavaMembersGetter extends MembersGetter {
  private final PsiType myExpectedType;

  public JavaMembersGetter(@NotNull PsiType expectedType) {
    myExpectedType = expectedType;
  }

  public void addMembers(PsiElement position, Consumer<LookupElement> results) {
    final PsiClass psiClass = PsiUtil.resolveClassInType(myExpectedType);
    processMembers(position, results, psiClass, PsiTreeUtil.getParentOfType(position, PsiAnnotation.class) != null);

    if (myExpectedType instanceof PsiPrimitiveType && PsiType.DOUBLE.isAssignableFrom(myExpectedType)) {
      addConstantsFromTargetClass(position, results);
    }
  }

  private void addConstantsFromTargetClass(PsiElement position, Consumer<LookupElement> results) {
    PsiElement parent = position.getParent();
    if (!(parent instanceof PsiReferenceExpression)) {
      return;
    }

    PsiElement prev = parent;
    parent = parent.getParent();
    while (parent instanceof PsiBinaryExpression) {
      final PsiBinaryExpression binaryExpression = (PsiBinaryExpression)parent;
      final IElementType op = binaryExpression.getOperationTokenType();
      if (JavaTokenType.EQEQ == op || JavaTokenType.NE == op) {
        if (prev == binaryExpression.getROperand()) {
          processMembers(position, results, getCalledClass(binaryExpression.getLOperand()), false);
        }
        return;
      }
      prev = parent;
      parent = parent.getParent();
    }
    if (parent instanceof PsiExpressionList) {
      processMembers(position, results, getCalledClass(parent.getParent()), false);
    }
  }

  @Nullable
  private static PsiClass getCalledClass(@Nullable PsiElement call) {
    if (call instanceof PsiMethodCallExpression) {
      for (final JavaResolveResult result : ((PsiMethodCallExpression)call).getMethodExpression().multiResolve(true)) {
        final PsiElement element = result.getElement();
        if (element instanceof PsiMethod) {
          final PsiClass aClass = ((PsiMethod)element).getContainingClass();
          if (aClass != null) {
            return aClass;
          }
        }
      }
    }
    if (call instanceof PsiNewExpression) {
      final PsiJavaCodeReferenceElement reference = ((PsiNewExpression)call).getClassReference();
      if (reference != null) {
        for (final JavaResolveResult result : reference.multiResolve(true)) {
          final PsiElement element = result.getElement();
          if (element instanceof PsiClass) {
            return (PsiClass)element;
          }
        }
      }
    }
    return null;
  }

  @Nullable
  protected LookupElement createFieldElement(PsiField field) {
    if (!myExpectedType.isAssignableFrom(field.getType())) {
      return null;
    }

    return JavaCompletionUtil.qualify(new VariableLookupItem(field));
  }

  @Nullable
  protected LookupElement createMethodElement(PsiMethod method) {
    PsiSubstitutor substitutor = SmartCompletionDecorator.calculateMethodReturnTypeSubstitutor(method, myExpectedType);
    PsiType type = substitutor.substitute(method.getReturnType());
    if (type == null || !myExpectedType.isAssignableFrom(type)) {
      return null;
    }


    JavaMethodCallElement item = new JavaMethodCallElement(method);
    item.setInferenceSubstitutor(substitutor);
    return JavaCompletionUtil.qualify(item);
  }
}
