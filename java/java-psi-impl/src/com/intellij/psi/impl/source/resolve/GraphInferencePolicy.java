/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.psi.impl.source.resolve;

import com.intellij.psi.*;
import com.intellij.psi.scope.MethodProcessorSetupFailedException;
import com.intellij.psi.scope.processor.MethodCandidatesProcessor;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
* User: anna
* Date: 2/14/13
*/
public class GraphInferencePolicy extends ProcessCandidateParameterTypeInferencePolicy {
  private static final ThreadLocal<Map<PsiExpression, Map<JavaResolveResult, PsiSubstitutor>>> ourResults = new ThreadLocal<Map<PsiExpression, Map<JavaResolveResult, PsiSubstitutor>>>() {
    @Override
    protected Map<PsiExpression, Map<JavaResolveResult, PsiSubstitutor>> initialValue() {
      return new WeakHashMap<PsiExpression, Map<JavaResolveResult, PsiSubstitutor>>();
    }
  };

  @Override
  protected List<PsiExpression> getExpressions(PsiExpression[] expressions, int i) {
    final List<PsiExpression> list = Arrays.asList(expressions);
    list.set(i, null);
    return list;
  }

  @Override
  protected PsiSubstitutor getSubstitutor(PsiCallExpression contextCall, PsiExpression[] expressions, int i, JavaResolveResult result) {
    Map<JavaResolveResult, PsiSubstitutor> map = ourResults.get().get(contextCall);
    if (map != null) {
      final PsiSubstitutor substitutor = map.get(result);
      if (substitutor != PsiSubstitutor.UNKNOWN && substitutor != null && substitutor.isValid()) return substitutor;
    }
    final PsiSubstitutor substitutor = super.getSubstitutor(contextCall, expressions, i, result);
    if (map != null) {
      map.put(result, substitutor);
    }
    return substitutor;
  }

  @NotNull
  @Override
  protected JavaResolveResult[] getResults(@NotNull PsiCallExpression contextCall, final int exprIdx)
    throws MethodProcessorSetupFailedException {
    Map<JavaResolveResult, PsiSubstitutor> map = ourResults.get().get(contextCall);
    if (map != null) {
      final Set<JavaResolveResult> results = map.keySet();
      return results.toArray(new JavaResolveResult[results.size()]);
    }

    PsiFile containingFile = contextCall.getContainingFile();
    final MethodCandidatesProcessor processor = new MethodCandidatesProcessor(contextCall, containingFile) {
      @Override
      protected PsiType[] getExpressionTypes(PsiExpressionList argumentList) {
        if (argumentList != null) {
          final PsiExpression[] expressions = argumentList.getExpressions();
          final PsiType[] types = new PsiType[expressions.length];
          for (int i = 0; i < expressions.length; i++) {
            if (i != exprIdx) {
              types[i] = expressions[i].getType();
            }
            else {
              types[i] = PsiType.NULL;
            }
          }
          return types;
        }
        else {
          return null;
        }
      }
    };
    PsiScopesUtil.setupAndRunProcessor(processor, contextCall, false);
    final JavaResolveResult[] results = processor.getResult();

    map = new WeakHashMap<JavaResolveResult, PsiSubstitutor>();
    ourResults.get().put(contextCall, map);
    for (JavaResolveResult result : results) {
      map.put(result, PsiSubstitutor.UNKNOWN);
    }
    return results;
  }

  public static void forget(PsiElement parent) {
    if (parent instanceof PsiExpression) {
      PsiElement gParent = PsiUtil.skipParenthesizedExprUp(parent.getParent());
      if (gParent instanceof PsiExpressionList) {
        final PsiElement ggParent = gParent.getParent();
        if (ggParent instanceof PsiCallExpression) {
          ourResults.get().remove(ggParent);
        }
      }
    }
  }
}
