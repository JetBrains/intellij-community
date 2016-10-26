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
import com.intellij.lang.TreeBackedLighterAST;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.source.JavaLightTreeUtil;
import com.intellij.psi.impl.source.tree.LightTreeUtil;
import com.intellij.psi.impl.source.tree.RecursiveLighterASTNodeWalkingVisitor;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.psi.impl.source.tree.JavaElementType.*;

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

    return CachedValuesManager.getCachedValue(method, () -> {
      TreeBackedLighterAST tree = new TreeBackedLighterAST(method.getContainingFile().getNode());
      PsiCodeBlock body = method.getBody();
      PurityInferenceResult result = doInferPurity(TreeBackedLighterAST.wrap(body.getNode()), tree);
      Boolean pure = RecursionManager.doPreventingRecursion(method, true, () -> result != null && result.isPure(method, body));
      return CachedValueProvider.Result.create(pure == Boolean.TRUE, method);
    });
  }

  @Nullable
  private static PurityInferenceResult doInferPurity(LighterASTNode body, LighterAST tree) {
    List<LighterASTNode> mutatedRefs = new ArrayList<>();
    Ref<Boolean> hasReturns = Ref.create(false);
    List<LighterASTNode> calls = new ArrayList<>();
    new RecursiveLighterASTNodeWalkingVisitor(tree) {
      @Override
      public void visitNode(@NotNull LighterASTNode element) {
        IElementType type = element.getTokenType();
        if (type == ASSIGNMENT_EXPRESSION) {
          mutatedRefs.add(tree.getChildren(element).get(0));
        }
        else if (type == RETURN_STATEMENT && JavaLightTreeUtil.findExpressionChild(tree, element) != null) {
          hasReturns.set(true);
        }
        else if ((type == PREFIX_EXPRESSION || type == POSTFIX_EXPRESSION) && isMutatingOperation(element)) {
          ContainerUtil.addIfNotNull(mutatedRefs, JavaLightTreeUtil.findExpressionChild(tree, element));
        }
        else if (isCall(element, type)) {
          calls.add(element);
        }

        super.visitNode(element);
      }

      private boolean isCall(@NotNull LighterASTNode element, IElementType type) {
        return type == NEW_EXPRESSION && LightTreeUtil.firstChildOfType(tree, element, EXPRESSION_LIST) != null ||
               type == METHOD_CALL_EXPRESSION;
      }

      private boolean isMutatingOperation(@NotNull LighterASTNode element) {
        return LightTreeUtil.firstChildOfType(tree, element, JavaTokenType.PLUSPLUS) != null ||
               LightTreeUtil.firstChildOfType(tree, element, JavaTokenType.MINUSMINUS) != null;
      }

    }.visitNode(body);

    if (calls.size() > 1 || !hasReturns.get()) return null;
    
    int bodyStart = body.getStartOffset();
    return new PurityInferenceResult(ContainerUtil.map(mutatedRefs, node -> ExpressionRange.create(node, bodyStart)),
                                     calls.isEmpty() ? null : ExpressionRange.create(calls.get(0), bodyStart));
  }

}

