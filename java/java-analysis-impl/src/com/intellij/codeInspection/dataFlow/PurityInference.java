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

import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author peter
 */
public class PurityInference {

  public static boolean inferPurity(@NotNull final PsiMethod method) {
    if (!InferenceFromSourceUtil.shouldInferFromSource(method) ||
        PsiType.VOID.equals(method.getReturnType()) ||
        method.getBody() == null ||
        method.isConstructor() || 
        PropertyUtil.isSimpleGetter(method)) {
      return false;
    }

    return CachedValuesManager.getCachedValue(method, new CachedValueProvider<Boolean>() {
      @Nullable
      @Override
      public Result<Boolean> compute() {
        boolean pure = RecursionManager.doPreventingRecursion(method, true, new Computable<Boolean>() {
          @Override
          public Boolean compute() {
            return doInferPurity(method);
          }
        }) == Boolean.TRUE;
        return Result.create(pure, method);
      }
    });
  }

  private static boolean doInferPurity(PsiMethod method) {
    PsiCodeBlock body = method.getBody();
    if (body == null) return false;

    final Ref<Boolean> impureFound = Ref.create(false);
    final Ref<Boolean> hasReturns = Ref.create(false);
    final List<PsiCallExpression> calls = ContainerUtil.newArrayList();
    body.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitAssignmentExpression(PsiAssignmentExpression expression) {
        if (!isLocalVarReference(expression.getLExpression())) {
          impureFound.set(true);
        }
        super.visitAssignmentExpression(expression);
      }

      @Override
      public void visitReturnStatement(PsiReturnStatement statement) {
        if (statement.getReturnValue() != null) {
          hasReturns.set(true);
        }
        super.visitReturnStatement(statement);
      }

      @Override
      public void visitPrefixExpression(PsiPrefixExpression expression) {
        if (isMutatingOperation(expression.getOperationTokenType()) && !isLocalVarReference(expression.getOperand())) {
          impureFound.set(true);
        }
        super.visitPrefixExpression(expression);
      }

      private boolean isMutatingOperation(IElementType operationTokenType) {
        return operationTokenType == JavaTokenType.PLUSPLUS || operationTokenType == JavaTokenType.MINUSMINUS;
      }

      @Override
      public void visitPostfixExpression(PsiPostfixExpression expression) {
        if (isMutatingOperation(expression.getOperationTokenType()) && !isLocalVarReference(expression.getOperand())) {
          impureFound.set(true);
        }
        super.visitPostfixExpression(expression);
      }

      @Override
      public void visitCallExpression(PsiCallExpression callExpression) {
        if (!(callExpression instanceof PsiNewExpression) || ((PsiNewExpression)callExpression).getArrayDimensions().length == 0) {
          calls.add(callExpression);
        }
        super.visitCallExpression(callExpression);
      }
    });

    if (impureFound.get() || calls.size() > 1 || !hasReturns.get()) return false;
    if (calls.isEmpty()) return true;

    final PsiMethod called = calls.get(0).resolveMethod();
    return called != null && ControlFlowAnalyzer.isPure(called);
  }

  private static boolean isLocalVarReference(PsiExpression expression) {
    if (expression instanceof PsiReferenceExpression) {
      PsiElement target = ((PsiReferenceExpression)expression).resolve();
      return target instanceof PsiLocalVariable || target instanceof PsiParameter;
    }
    if (expression instanceof PsiArrayAccessExpression) {
      return isLocalVarReference(((PsiArrayAccessExpression)expression).getArrayExpression());
    }
    return false;
  }
}
