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
package com.intellij.psi.impl.source.resolve;

import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.scope.MethodProcessorSetupFailedException;
import com.intellij.psi.scope.processor.MethodCandidatesProcessor;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ConcurrentHashMap;
import com.intellij.util.containers.ConcurrentWeakHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * User: anna
 * Date: 7/18/12
 */
public class ProcessCandidateParameterTypeInferencePolicy extends DefaultParameterTypeInferencePolicy {
  public static final ProcessCandidateParameterTypeInferencePolicy INSTANCE = new ProcessCandidateParameterTypeInferencePolicy();
  private static final Map<PsiExpression, Map<JavaResolveResult, PsiSubstitutor>> ourResults =
    new ConcurrentWeakHashMap<PsiExpression, Map<JavaResolveResult, PsiSubstitutor>>();
  
  @Override
  public Pair<PsiType, ConstraintType> inferTypeConstraintFromCallContext(PsiExpression innerMethodCall,
                                                                          PsiExpressionList expressionList,
                                                                          PsiCallExpression contextCall,
                                                                          PsiTypeParameter typeParameter) {
    PsiExpression[] expressions = expressionList.getExpressions();
    int i = ArrayUtil.find(expressions, innerMethodCall);
    if (i < 0) return null;
    PsiMethod owner = (PsiMethod)typeParameter.getOwner();
    if (owner == null) return null;

    try {
      final Map<JavaResolveResult, PsiSubstitutor> results = getCachedResults(contextCall);
      final PsiType innerReturnType = owner.getReturnType();
      for (final JavaResolveResult result : results.keySet()) {
        final PsiSubstitutor subst = results.get(result);
        final PsiSubstitutor substitutor;
        if (subst == PsiSubstitutor.UNKNOWN) {
          if (result instanceof MethodCandidateInfo) {
            List<PsiExpression> leftArgs = getExpressions(expressions, i);
            substitutor = ((MethodCandidateInfo)result).inferTypeArguments(this, leftArgs.toArray(new PsiExpression[leftArgs.size()]));
          }
          else {
            substitutor = result.getSubstitutor();
          }
          results.put(result, substitutor);
        } else {
          substitutor = subst;
        }

        final Pair<PsiType, ConstraintType> constraint = inferConstraint(typeParameter, innerMethodCall, i, innerReturnType, result, substitutor);
        if (constraint != null) return constraint;
      }
    }
    catch (MethodProcessorSetupFailedException ev) {
      return null;
    }

    return null;
  }

  protected List<PsiExpression> getExpressions(PsiExpression[] expressions, int i) {
    return Arrays.asList(expressions).subList(0, i);
  }

  private static Pair<PsiType, ConstraintType> inferConstraint(PsiTypeParameter typeParameter, 
                                                               PsiExpression innerMethodCall,
                                                               int parameterIdx,
                                                               PsiType innerReturnType,
                                                               JavaResolveResult result,
                                                               final PsiSubstitutor substitutor) {
    final PsiElement element = result.getElement();
    if (element instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod)element;
      final PsiParameter[] parameters = method.getParameterList().getParameters();
      PsiParameter parameter = null;
      if (parameters.length > parameterIdx) {
        parameter = parameters[parameterIdx];
      }
      else if (method.isVarArgs()) {
        parameter = parameters[parameters.length - 1];
      }
      if (parameter != null) {
        final PsiParameter finalParameter = parameter;
        PsiType type = PsiResolveHelper.ourGuard.doPreventingRecursion(innerMethodCall, true, new Computable<PsiType>() {
          @Override
          public PsiType compute() {
            return substitutor.substitute(finalParameter.getType());
          }
        });
        final Pair<PsiType, ConstraintType> constraint =
          PsiResolveHelperImpl.getSubstitutionForTypeParameterConstraint(typeParameter, innerReturnType, type, false,
                                                                         PsiUtil.getLanguageLevel(finalParameter));
        if (constraint != null) return constraint;
      }
    }
    return null;
  }

  @NotNull
  private static Map<JavaResolveResult, PsiSubstitutor> getCachedResults(PsiCallExpression contextCall) throws MethodProcessorSetupFailedException {
    final PsiCallExpression preparedKey = contextCall.getCopyableUserData(PsiResolveHelperImpl.CALL_EXPRESSION_KEY);
    Map<JavaResolveResult, PsiSubstitutor> map;
    if (preparedKey != null) {
      map = ourResults.get(preparedKey);
      if (map != null) {
        return map;
      }
    }

    map = new ConcurrentHashMap<JavaResolveResult, PsiSubstitutor>();
    if (preparedKey != null) {
      ourResults.put(preparedKey, map);
    }

    final MethodCandidatesProcessor processor = new MethodCandidatesProcessor(contextCall);
    //can't call resolve() since it obtains full substitution, that may result in infinite recursion
    PsiScopesUtil.setupAndRunProcessor(processor, contextCall, false);
    for (JavaResolveResult resolveResult : processor.getResult()) {
      map.put(resolveResult, PsiSubstitutor.UNKNOWN);
    }
    return map;
  }
  
  @Nullable
  public static Pair<PsiType, ConstraintType> inferConstraint(PsiTypeParameter typeParameter, PsiCallExpression parentExpression, PsiExpression nullPlaceHolder, int exprIdx, PsiType methodReturnType) {
    final Map<JavaResolveResult, PsiSubstitutor> map = ourResults.get(parentExpression);
    if (map != null) {
      for (JavaResolveResult resolveResult : map.keySet()) {
        final PsiSubstitutor substitutor = map.get(resolveResult);
        if (substitutor == PsiSubstitutor.UNKNOWN) return null;
        if (!substitutor.isValid()) {
          ourResults.remove(parentExpression);
          return null;
        }
        final Pair<PsiType, ConstraintType> constraint = inferConstraint(typeParameter, nullPlaceHolder, exprIdx, methodReturnType, resolveResult, substitutor);
        if (constraint != null) return constraint;
      }
    }
    return null;
  }
}
