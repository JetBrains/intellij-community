/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.ExpectedTypesProvider;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.scope.MethodProcessorSetupFailedException;
import com.intellij.psi.scope.processor.MethodCandidatesProcessor;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;

import java.util.Arrays;
import java.util.List;

/**
 * @author yole
 */
public class CompletionParameterTypeInferencePolicy extends ParameterTypeInferencePolicy {
  public static final CompletionParameterTypeInferencePolicy INSTANCE = new CompletionParameterTypeInferencePolicy();

  private CompletionParameterTypeInferencePolicy() {
  }

  @Override
  public Pair<PsiType, ConstraintType> inferTypeConstraintFromCallContext(PsiCallExpression innerMethodCall,
                                                                                               PsiExpressionList expressionList,
                                                                                               PsiCallExpression contextCall,
                                                                                               PsiTypeParameter typeParameter) {
    final MethodCandidatesProcessor processor = new MethodCandidatesProcessor(contextCall);
    try {
      //can't call resolve() since it obtains full substitution, that may result in infinite recursion
      PsiScopesUtil.setupAndRunProcessor(processor, contextCall, false);
      PsiExpression[] expressions = expressionList.getExpressions();
      int i = ArrayUtil.find(expressions, innerMethodCall);
      assert i >= 0;
      final JavaResolveResult[] results = processor.getResult();
      PsiMethod owner = (PsiMethod)typeParameter.getOwner();
      if (owner == null) return null;

      final PsiType innerReturnType = owner.getReturnType();
      for (final JavaResolveResult result : results) {
        final PsiSubstitutor substitutor;
        if (result instanceof MethodCandidateInfo) {
          List<PsiExpression> leftArgs = Arrays.asList(expressions).subList(0, i);
          substitutor = ((MethodCandidateInfo)result).inferTypeArguments(this, leftArgs.toArray(new PsiExpression[leftArgs.size()]));
        } else {
          substitutor = result.getSubstitutor();
        }

        final PsiElement element = result.getElement();
        if (element instanceof PsiMethod) {
          final PsiMethod method = (PsiMethod)element;
          final PsiParameter[] parameters = method.getParameterList().getParameters();
          PsiParameter parameter = null;
          if (parameters.length > i) {
            parameter = parameters[i];
          }
          else if (method.isVarArgs()) {
            parameter = parameters[parameters.length - 1];
          }
          if (parameter != null) {
            final PsiParameter finalParameter = parameter;
            PsiType type = PsiResolveHelperImpl.ourGuard.doPreventingRecursion(innerMethodCall, true, new Computable<PsiType>() {
              @Override
              public PsiType compute() {
                return substitutor.substitute(finalParameter.getType());
              }
            });
            final Pair<PsiType, ConstraintType> constraint =
              PsiResolveHelperImpl.getSubstitutionForTypeParameterConstraint(typeParameter, innerReturnType, type, false,
                                                                             PsiUtil.getLanguageLevel(innerMethodCall));
            if (constraint != null) return constraint;
          }
        }
      }
    }
    catch (MethodProcessorSetupFailedException ev) {
      return null;
    }

    return null;
  }

  @Override
  public PsiType getDefaultExpectedType(PsiCallExpression methodCall) {
    ExpectedTypeInfo[] expectedTypes = ExpectedTypesProvider.getExpectedTypes(methodCall, true);
    if (expectedTypes.length > 0) {
      return expectedTypes[0].getType();
    }
    return PsiType.NULL;
  }

  @Override
  public Pair<PsiType, ConstraintType> getInferredTypeWithNoConstraint(PsiManager psiManager, PsiType superType) {
    if (!(superType instanceof PsiWildcardType)) {
      return new Pair<PsiType, ConstraintType>(PsiWildcardType.createExtends(psiManager, superType), ConstraintType.EQUALS);
    }
    else {
      return new Pair<PsiType, ConstraintType>(superType, ConstraintType.SUBTYPE);
    }
  }

  @Override
  public PsiType adjustInferredType(PsiManager manager, PsiType guess, ConstraintType constraintType) {
    if (guess != null && !(guess instanceof PsiWildcardType)) {
      if (constraintType == ConstraintType.SUPERTYPE) return PsiWildcardType.createExtends(manager, guess);
      else if (constraintType == ConstraintType.SUBTYPE) return PsiWildcardType.createSuper(manager, guess);
    }
    return guess;
  }
}
