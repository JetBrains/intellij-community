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

import com.intellij.openapi.util.Pair;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.scope.MethodProcessorSetupFailedException;
import com.intellij.psi.scope.processor.MethodCandidatesProcessor;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

public class ProcessCandidateParameterTypeInferencePolicy extends DefaultParameterTypeInferencePolicy {
  public static final ProcessCandidateParameterTypeInferencePolicy INSTANCE = new ProcessCandidateParameterTypeInferencePolicy();


  @Override
  public Pair<PsiType, ConstraintType> inferTypeConstraintFromCallContext(PsiExpression innerMethodCall,
                                                                          PsiExpressionList expressionList,
                                                                          @NotNull PsiCallExpression contextCall,
                                                                          PsiTypeParameter typeParameter) {
    PsiExpression[] expressions = expressionList.getExpressions();
    PsiElement parent = innerMethodCall;
    while (parent.getParent() instanceof PsiParenthesizedExpression) {
      parent = parent.getParent();
    }
    int i = ArrayUtilRt.find(expressions, parent);
    if (i < 0) return null;
    PsiMethod owner = (PsiMethod)typeParameter.getOwner();
    if (owner == null) return null;

    try {
      final JavaResolveResult[] results = getResults(contextCall, i);
      final PsiType innerReturnType = owner.getReturnType();
      for (final JavaResolveResult result : results) {
        if (result == null) continue;
        final PsiSubstitutor substitutor = getSubstitutor(contextCall, expressions, i, result);
        final Pair<PsiType, ConstraintType> constraint = inferConstraint(typeParameter, innerMethodCall, i, innerReturnType, result, substitutor);
        if (constraint != null) return constraint;
      }
    }
    catch (MethodProcessorSetupFailedException ev) {
      return null;
    }

    return null;
  }

  protected PsiSubstitutor getSubstitutor(PsiCallExpression contextCall, PsiExpression[] expressions, int i, JavaResolveResult result) {
    if (result instanceof MethodCandidateInfo) {
      List<PsiExpression> leftArgs = getExpressions(expressions, i);
      return ((MethodCandidateInfo)result).inferSubstitutorFromArgs(this, leftArgs.toArray(new PsiExpression[leftArgs.size()]));
    }
    else {
      return result.getSubstitutor();
    }
  }

  protected List<PsiExpression> getExpressions(PsiExpression[] expressions, int i) {
    return Arrays.asList(expressions).subList(0, i);
  }

  protected static Pair<PsiType, ConstraintType> inferConstraint(PsiTypeParameter typeParameter,
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
        PsiType type = PsiResolveHelper.ourGuard.doPreventingRecursion(innerMethodCall, true,
                                                                       () -> substitutor.substitute(finalParameter.getType()));
        final LanguageLevel languageLevel = PsiUtil.getLanguageLevel(finalParameter);
        final Pair<PsiType, ConstraintType> constraint =
          new PsiOldInferenceHelper(element.getManager()).getSubstitutionForTypeParameterConstraint(typeParameter, innerReturnType, type, false, languageLevel);
        if (constraint != null) return constraint;
      }
    }
    return null;
  }

  @NotNull
  protected JavaResolveResult[] getResults(@NotNull PsiCallExpression contextCall, final int exprIdx) throws MethodProcessorSetupFailedException {
    PsiFile containingFile = contextCall.getContainingFile();
    final MethodCandidatesProcessor processor = new MethodCandidatesProcessor(contextCall, containingFile);
    //can't call resolve() since it obtains full substitution, that may result in infinite recursion
    PsiScopesUtil.setupAndRunProcessor(processor, contextCall, false);
    return processor.getResult();
  }
}
