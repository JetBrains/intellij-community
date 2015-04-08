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
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.psi.*;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author peter
 */
public class NullityInference {

  public static Nullness inferNullity(final PsiMethod method) {
    if (!InferenceFromSourceUtil.shouldInferFromSource(method)) {
      return Nullness.UNKNOWN;
    }

    PsiType type = method.getReturnType();
    if (type == null || type instanceof PsiPrimitiveType) {
      return Nullness.UNKNOWN;
    }

    return CachedValuesManager.getCachedValue(method, new CachedValueProvider<Nullness>() {
      @Nullable
      @Override
      public Result<Nullness> compute() {
        Nullness result = RecursionManager.doPreventingRecursion(method, true, new Computable<Nullness>() {
          @Override
          public Nullness compute() {
            return doInferNullity(method);
          }
        });
        if (result == null) result = Nullness.UNKNOWN;
        return Result.create(result, method, PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT);
      }
    });
  }

  @NotNull 
  private static Nullness doInferNullity(PsiMethod method) {
    PsiCodeBlock body = method.getBody();
    if (body != null) {
      final AtomicBoolean hasErrors = new AtomicBoolean();
      final AtomicBoolean hasNotNulls = new AtomicBoolean();
      final AtomicBoolean hasNulls = new AtomicBoolean();
      final AtomicBoolean hasUnknowns = new AtomicBoolean();
      final Set<PsiMethod> delegates = ContainerUtil.newLinkedHashSet();
      body.accept(new JavaRecursiveElementWalkingVisitor() {
        @Override
        public void visitReturnStatement(PsiReturnStatement statement) {
          PsiExpression value = statement.getReturnValue();
          if (value == null) {
            hasErrors.set(true);
          } else if (value instanceof PsiLiteralExpression) {
            if (value.textMatches(PsiKeyword.NULL)) {
              hasNulls.set(true);
            }
            else {
              hasNotNulls.set(true);
            }
          }
          else if (value instanceof PsiLambdaExpression || value.getType() instanceof PsiPrimitiveType) {
            hasNotNulls.set(true);
          }
          else if (containsNulls(value)) {
            hasNulls.set(true);
          }
          else if (value instanceof PsiMethodCallExpression) {
            PsiMethod target = ((PsiMethodCallExpression)value).resolveMethod();
            if (target == null) {
              hasUnknowns.set(true);
            }
            else {
              delegates.add(target);
            }
          }
          else {
            hasUnknowns.set(true);
          }
          super.visitReturnStatement(statement);
        }

        private boolean containsNulls(PsiExpression value) {
          if (value instanceof PsiConditionalExpression) {
            return containsNulls(((PsiConditionalExpression)value).getElseExpression()) || containsNulls(((PsiConditionalExpression)value).getThenExpression());
          }
          if (value instanceof PsiParenthesizedExpression) {
            return containsNulls(((PsiParenthesizedExpression)value).getExpression());
          }
          return value instanceof PsiLiteralExpression && value.textMatches(PsiKeyword.NULL);
        }

        @Override
        public void visitClass(PsiClass aClass) {
        }

        @Override
        public void visitLambdaExpression(PsiLambdaExpression expression) {
        }

        @Override
        public void visitErrorElement(PsiErrorElement element) {
          hasErrors.set(true);
          super.visitErrorElement(element);
        }
      });
      
      if (hasNulls.get()) {
        return InferenceFromSourceUtil.suppressNullable(method) ? Nullness.UNKNOWN : Nullness.NULLABLE;
      }
      
      if (hasErrors.get() || hasUnknowns.get() || delegates.size() > 1) {
        return Nullness.UNKNOWN;
      }

      if (delegates.size() == 1) {
        if (NullableNotNullManager.isNotNull(delegates.iterator().next())) {
          return Nullness.NOT_NULL;
        }
        return Nullness.UNKNOWN;
      }

      if (hasNotNulls.get()) {
        return Nullness.NOT_NULL;
      }
      
    }
    return Nullness.UNKNOWN;
  }
}
