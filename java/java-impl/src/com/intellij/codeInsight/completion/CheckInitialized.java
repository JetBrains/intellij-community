/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.ExpressionUtil;
import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.psi.*;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * @author peter
 */
class CheckInitialized implements ElementFilter {
  private final Set<PsiField> myNonInitializedFields;
  private final boolean myInsideConstructorCall;

  CheckInitialized(@NotNull PsiElement position) {
    myNonInitializedFields = getNonInitializedFields(position);
    myInsideConstructorCall = isInsideConstructorCall(position);
  }

  static boolean isInsideConstructorCall(@NotNull PsiElement position) {
    return ExpressionUtils.isConstructorInvocation(PsiTreeUtil.getParentOfType(position, PsiMethodCallExpression.class)) &&
           !JavaKeywordCompletion.AFTER_DOT.accepts(position);
  }

  private static boolean isInitializedImplicitly(PsiField field) {
    field = CompletionUtil.getOriginalOrSelf(field);
    for(ImplicitUsageProvider provider: ImplicitUsageProvider.EP_NAME.getExtensions()) {
      if (provider.isImplicitWrite(field)) {
        return true;
      }
    }
    return false;
  }

  static Set<PsiField> getNonInitializedFields(PsiElement element) {
    PsiReferenceExpression ref = PsiTreeUtil.getNonStrictParentOfType(element, PsiReferenceExpression.class);
    if (ref == null) return Collections.emptySet();

    final PsiStatement statement = PsiTreeUtil.getParentOfType(element, PsiStatement.class);
    //noinspection SSBasedInspection
    final PsiMethod method = PsiTreeUtil.getParentOfType(element, PsiMethod.class, true, PsiClass.class);
    if (statement == null || method == null || !method.isConstructor()) {
      return Collections.emptySet();
    }

    PsiElement parent = element.getParent();
    if (parent instanceof PsiReferenceExpression && !ExpressionUtil.isEffectivelyUnqualified((PsiReferenceExpression)parent)) {
      return Collections.emptySet();
    }

    while (parent != statement) {
      PsiElement next = parent.getParent();
      if (next instanceof PsiAssignmentExpression && parent == ((PsiAssignmentExpression)next).getLExpression()) {
        return Collections.emptySet();
      }
      if (parent instanceof PsiJavaCodeReferenceElement) {
        PsiStatement psiStatement = PsiTreeUtil.getParentOfType(parent, PsiStatement.class);
        if (psiStatement != null && psiStatement.getTextRange().getStartOffset() == parent.getTextRange().getStartOffset()) {
          return Collections.emptySet();
        }
      }
      parent = next;
    }

    boolean allowNonFinalFields = !isInsideConstructorCall(element);

    final Set<PsiField> fields = new HashSet<>();
    final PsiClass containingClass = method.getContainingClass();
    assert containingClass != null;
    for (PsiField field : containingClass.getFields()) {
      if (!field.hasModifierProperty(PsiModifier.STATIC) && field.getInitializer() == null && !isInitializedImplicitly(field)) {
        if (!allowNonFinalFields || field.hasModifierProperty(PsiModifier.FINAL)) {
          fields.add(field);
        }
      }
    }

    method.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitAssignmentExpression(PsiAssignmentExpression expression) {
        PsiExpression lExpression = expression.getLExpression();
        if (lExpression instanceof PsiReferenceExpression && ExpressionUtil.isEffectivelyUnqualified((PsiReferenceExpression)lExpression)) {
          PsiElement target = ((PsiReferenceExpression)lExpression).resolve();
          if (target instanceof PsiField) {
            if (expression.getTextRange().getStartOffset() < statement.getTextRange().getStartOffset()) {
              fields.remove(target);
            }
            else if (ref == PsiUtil.deparenthesizeExpression(expression.getRExpression())) {
              fields.add((PsiField)target);
            }
          }
        }
        super.visitAssignmentExpression(expression);
      }

      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression expression) {
        if (expression.getTextRange().getStartOffset() < statement.getTextRange().getStartOffset()) {
          final PsiReferenceExpression methodExpression = expression.getMethodExpression();
          if (methodExpression.textMatches(PsiKeyword.THIS)) {
            fields.clear();
          }
        }
        super.visitMethodCallExpression(expression);
      }
    });
    return fields;
  }

  @Override
  public boolean isAcceptable(Object element, @Nullable PsiElement context) {
    if (element instanceof CandidateInfo) {
      element = ((CandidateInfo)element).getElement();
    }
    if (element instanceof PsiField) {
      return !myNonInitializedFields.contains(element);
    }
    if (element instanceof PsiMethod && myInsideConstructorCall) {
      return ((PsiMethod)element).hasModifierProperty(PsiModifier.STATIC);
    }

    return true;
  }

  @Override
  public boolean isClassAcceptable(Class hintClass) {
    return true;
  }
}
