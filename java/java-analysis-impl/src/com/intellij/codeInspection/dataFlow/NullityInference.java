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

import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.JavaLightTreeUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

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
      MethodData data = ContractInferenceIndexKt.getIndexedData(method);
      NullityInferenceResult result = data == null ? null : data.getNullity();
      Nullness nullness = result == null ? null : RecursionManager.doPreventingRecursion(method, true, () -> result.getNullness(method, data.methodBody(method)));
      if (nullness == null) nullness = Nullness.UNKNOWN;
      return CachedValueProvider.Result.create(nullness, method, PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT);
    });
  }

  static class NullityInferenceVisitor {
    private final LighterAST tree;
    private final LighterASTNode body;
    private boolean hasErrors;
    private boolean hasNotNulls;
    private boolean hasNulls;
    private boolean hasUnknowns;
    MultiMap<String, ExpressionRange> delegates = MultiMap.create();

    NullityInferenceVisitor(LighterAST tree, LighterASTNode body) {
      this.tree = tree;
      this.body = body;
    }

    void visitNode(LighterASTNode element) {
      IElementType type = element.getTokenType();

      if (type == TokenType.ERROR_ELEMENT) {
        hasErrors = true;
      }
      else if (type == RETURN_STATEMENT) {
        LighterASTNode value = JavaLightTreeUtil.findExpressionChild(tree, element);
        if (value == null) {
          hasErrors= true;
        } else {
          visitReturnedValue(value);
        }
      }
    }

    private void visitReturnedValue(LighterASTNode expr) {
      IElementType type = expr.getTokenType();
      if (containsNulls(expr)) {
        hasNulls = true;
      }
      else if (type == LAMBDA_EXPRESSION || type == NEW_EXPRESSION ||
               type == LITERAL_EXPRESSION || type == BINARY_EXPRESSION || type == POLYADIC_EXPRESSION) {
        hasNotNulls = true;
      }
      else if (type == METHOD_CALL_EXPRESSION) {
        String calledMethod = JavaLightTreeUtil.getNameIdentifierText(tree, tree.getChildren(expr).get(0));
        if (calledMethod != null) {
          delegates.putValue(calledMethod, ExpressionRange.create(expr, body.getStartOffset()));
        }
      }
      else {
        hasUnknowns = true;
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

    @Nullable
    NullityInferenceResult getResult() {
      if (hasNulls) {
        return new NullityInferenceResult.Predefined(Nullness.NULLABLE);
      }
      if (hasErrors || hasUnknowns || delegates.size() > 1) {
        return null;
      }
      if (delegates.size() == 1) {
        return new NullityInferenceResult.FromDelegate(ContainerUtil.newArrayList(delegates.get(delegates.keySet().iterator().next())));
      }

      if (hasNotNulls) {
        return new NullityInferenceResult.Predefined(Nullness.NOT_NULL);
      }
      return null;
    }
  }
}
