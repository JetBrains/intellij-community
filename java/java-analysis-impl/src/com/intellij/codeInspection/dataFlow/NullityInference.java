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
import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import com.intellij.lang.TreeBackedLighterAST;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.JavaLightTreeUtil;
import com.intellij.psi.impl.source.tree.RecursiveLighterASTNodeWalkingVisitor;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.intellij.psi.impl.source.tree.JavaElementType.*;

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

    return CachedValuesManager.getCachedValue(method, () -> {
      TreeBackedLighterAST tree = new TreeBackedLighterAST(method.getContainingFile().getNode());
      PsiCodeBlock body = ObjectUtils.assertNotNull(method.getBody());
      NullityInferenceResult result = doInferNullity(tree, TreeBackedLighterAST.wrap(body.getNode()));
      Nullness nullness = result == null ? null : RecursionManager.doPreventingRecursion(method, true, () -> result.getNullness(method, body));
      if (nullness == null) nullness = Nullness.UNKNOWN;
      return CachedValueProvider.Result.create(nullness, method, PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT);
    });
  }

  @Nullable
  private static NullityInferenceResult doInferNullity(LighterAST tree, LighterASTNode body) {
    AtomicBoolean hasErrors = new AtomicBoolean();
    AtomicBoolean hasNotNulls = new AtomicBoolean();
    AtomicBoolean hasNulls = new AtomicBoolean();
    AtomicBoolean hasUnknowns = new AtomicBoolean();
    MultiMap<String, ExpressionRange> delegates = MultiMap.create();

    new RecursiveLighterASTNodeWalkingVisitor(tree) {
      @Override
      public void visitNode(@NotNull LighterASTNode element) {
        IElementType type = element.getTokenType();
        if (type == CLASS || type == ANONYMOUS_CLASS || type == LAMBDA_EXPRESSION) return;

        if (type == TokenType.ERROR_ELEMENT) {
          hasErrors.set(true);
        }
        else if (type == RETURN_STATEMENT) {
          LighterASTNode value = JavaLightTreeUtil.findExpressionChild(tree, element);
          if (value == null) {
            hasErrors.set(true);
          } else {
            visitReturnedValue(value);
          }
        }

        super.visitNode(element);
      }

      private void visitReturnedValue(LighterASTNode expr) {
        IElementType type = expr.getTokenType();
        if (containsNulls(expr)) {
          hasNulls.set(true);
        }
        else if (type == LAMBDA_EXPRESSION || type == NEW_EXPRESSION ||
                 type == LITERAL_EXPRESSION || type == BINARY_EXPRESSION || type == POLYADIC_EXPRESSION) {
          hasNotNulls.set(true);
        }
        else if (type == METHOD_CALL_EXPRESSION) {
          String calledMethod = JavaLightTreeUtil.getNameIdentifierText(tree, tree.getChildren(expr).get(0));
          if (calledMethod != null) {
            delegates.putValue(calledMethod, new ExpressionRange(expr, body.getStartOffset()));
          }
        }
        else {
          hasUnknowns.set(true);
        }
      }

      private boolean containsNulls(@NotNull LighterASTNode value) {
        if (value.getTokenType() == CONDITIONAL_EXPRESSION) {
          List<LighterASTNode> exprChildren = JavaLightTreeUtil.getExpressionChildren(tree, value);
          return exprChildren.subList(1, exprChildren.size()).stream().anyMatch(e -> containsNulls(e));
        }
        if (value.getTokenType() == PARENTH_EXPRESSION) {
          LighterASTNode wrapped = JavaLightTreeUtil.findExpressionChild(tree, value);
          return wrapped != null && containsNulls(wrapped);
        }
        return value.getTokenType() == LITERAL_EXPRESSION && tree.getChildren(value).get(0).getTokenType() == JavaTokenType.NULL_KEYWORD;
      }

    }.visitNode(body);


    if (hasNulls.get()) {
      return new NullityInferenceResult.Predefined(Nullness.NULLABLE);
    }
    if (hasErrors.get() || hasUnknowns.get() || delegates.size() > 1) {
      return null;
    }
    if (delegates.size() == 1) {
      return new NullityInferenceResult.FromDelegate(delegates.get(delegates.keySet().iterator().next()));
    }

    if (hasNotNulls.get()) {
      return new NullityInferenceResult.Predefined(Nullness.NOT_NULL);
    }
    return null;
  }
}

interface NullityInferenceResult {
  @NotNull
  Nullness getNullness(@NotNull PsiMethod method, @NotNull PsiCodeBlock body);

  class Predefined implements NullityInferenceResult {
    private final Nullness myNullness;

    Predefined(Nullness nullness) {
      myNullness = nullness;
    }

    @NotNull
    @Override
    public Nullness getNullness(@NotNull PsiMethod method, @NotNull PsiCodeBlock body) {
      return myNullness == Nullness.NULLABLE && InferenceFromSourceUtil.suppressNullable(method) ? Nullness.UNKNOWN : myNullness;
    }
  }

  class FromDelegate implements NullityInferenceResult {
    private final Collection<ExpressionRange> myDelegates;

    FromDelegate(Collection<ExpressionRange> delegates) {
      myDelegates = delegates;
    }

    @NotNull
    @Override
    public Nullness getNullness(@NotNull PsiMethod method, @NotNull PsiCodeBlock body) {
      return myDelegates.stream().allMatch(range -> isNotNullCall(range, body)) ? Nullness.NOT_NULL : Nullness.UNKNOWN;
    }

    private static boolean isNotNullCall(ExpressionRange delegate, @NotNull PsiCodeBlock body) {
      PsiMethodCallExpression call = (PsiMethodCallExpression)delegate.restoreExpression(body);
      if (call.getType() instanceof PsiPrimitiveType) return true;

      PsiMethod target = call.resolveMethod();
      return target != null && NullableNotNullManager.isNotNull(target);
    }
  }
}
