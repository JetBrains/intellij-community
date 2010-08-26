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
package com.intellij.psi.filters.getters;

import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.JavaMethodCallElement;
import com.intellij.codeInsight.completion.SmartCompletionDecorator;
import com.intellij.codeInsight.completion.JavaCompletionUtil;
import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.LookupItemUtil;
import com.intellij.psi.*;
import com.intellij.psi.filters.TrueFilter;
import com.intellij.psi.scope.processor.FilterScopeProcessor;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 15.04.2003
 * Time: 17:07:09
 * To change this template use Options | File Templates.
 */
public class MembersGetter {

  public static void addMembers(PsiElement position, PsiType expectedType, CompletionResultSet results) {
    final PsiClass psiClass = PsiUtil.resolveClassInType(expectedType);
    processMembers(position, results, psiClass, PsiTreeUtil.getParentOfType(position, PsiAnnotation.class) != null, expectedType);

    if (expectedType instanceof PsiPrimitiveType && PsiType.DOUBLE.isAssignableFrom(expectedType)) {
      addConstantsFromTargetClass(position, expectedType, results);
    }
  }

  private static void addConstantsFromTargetClass(PsiElement position, PsiType expectedType, CompletionResultSet results) {
    PsiElement parent = position.getParent();
    if (!(parent instanceof PsiReferenceExpression)) {
      return;
    }

    PsiElement prev = parent;
    parent = parent.getParent();
    while (parent instanceof PsiBinaryExpression) {
      final PsiBinaryExpression binaryExpression = (PsiBinaryExpression)parent;
      final IElementType op = binaryExpression.getOperationSign().getTokenType();
      if (JavaTokenType.EQEQ == op || JavaTokenType.NE == op) {
        if (prev == binaryExpression.getROperand()) {
          processMembers(position, results, getCalledClass(binaryExpression.getLOperand()), false, expectedType);
        }
        return;
      }
      prev = parent;
      parent = parent.getParent();
    }
    if (parent instanceof PsiExpressionList) {
      processMembers(position, results, getCalledClass(parent.getParent()), false, expectedType);
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

  private static void processMembers(final PsiElement context, final CompletionResultSet results, @Nullable final PsiClass where,
                                     final boolean acceptMethods, PsiType expectedType) {
    if (where == null) return;

    final FilterScopeProcessor<PsiElement> processor = new FilterScopeProcessor<PsiElement>(TrueFilter.INSTANCE);
    where.processDeclarations(processor, ResolveState.initial(), null, context);

    final PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(context.getProject()).getResolveHelper();
    for (final PsiElement result : processor.getResults()) {
      if (result instanceof PsiMember && !(result instanceof PsiClass)) {
        final PsiMember member = (PsiMember)result;
        if (member.hasModifierProperty(PsiModifier.STATIC) &&
            !PsiTreeUtil.isAncestor(member.getContainingClass(), context, false) &&
            resolveHelper.isAccessible(member, context, null)) {
          if (result instanceof PsiField && !member.hasModifierProperty(PsiModifier.FINAL)) continue;
          if (result instanceof PsiMethod && acceptMethods) continue;
          final LookupItem item = (LookupItem)LookupItemUtil.objectToLookupItem(result);
          item.setAutoCompletionPolicy(AutoCompletionPolicy.NEVER_AUTOCOMPLETE);
          JavaCompletionUtil.qualify(item);
          if (member instanceof PsiMethod) {
            final PsiMethod method = (PsiMethod)member;
            final PsiSubstitutor substitutor = SmartCompletionDecorator.calculateMethodReturnTypeSubstitutor(method, expectedType);
            ((JavaMethodCallElement) item).setInferenceSubstitutor(substitutor);
          }
          results.addElement(item);
        }
      }
    }
  }
}
