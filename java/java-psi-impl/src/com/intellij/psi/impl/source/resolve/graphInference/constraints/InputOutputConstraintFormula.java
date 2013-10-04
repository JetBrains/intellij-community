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
package com.intellij.psi.impl.source.resolve.graphInference.constraints;

import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.graphInference.FunctionalInterfaceParameterizationUtil;
import com.intellij.psi.impl.source.resolve.graphInference.InferenceSession;
import com.intellij.psi.impl.source.resolve.graphInference.InferenceVariable;
import com.intellij.psi.impl.source.resolve.graphInference.PsiPolyExpressionUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * User: anna
 * Date: 9/25/13
 */
public abstract class InputOutputConstraintFormula implements ConstraintFormula {

  protected abstract PsiExpression getExpression();
  protected abstract PsiType getT();
  protected abstract void setT(PsiType t);
  protected abstract InputOutputConstraintFormula createSelfConstraint(PsiType type, PsiExpression expression);
  protected abstract void collectReturnTypeVariables(InferenceSession session,
                                                     PsiExpression psiExpression,
                                                     PsiType returnType, 
                                                     Set<InferenceVariable> result);

  public Set<InferenceVariable> getInputVariables(InferenceSession session) {
    final PsiExpression psiExpression = getExpression();
    if (PsiPolyExpressionUtil.isPolyExpression(psiExpression)) {
      final PsiType type = getT();
      if (psiExpression instanceof PsiLambdaExpression || psiExpression instanceof PsiMethodReferenceExpression) {
        final InferenceVariable inferenceVariable = session.getInferenceVariable(type);
        if (inferenceVariable != null) {
          return Collections.singleton(inferenceVariable);
        }
        if (LambdaHighlightingUtil.checkInterfaceFunctional(type) == null) {
          final PsiType functionType =
            psiExpression instanceof PsiLambdaExpression
            ? FunctionalInterfaceParameterizationUtil.getFunctionalType(type, (PsiLambdaExpression)psiExpression)
            : type;
          final PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(functionType);
          final PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(resolveResult);
          if (interfaceMethod != null) {

            final Set<InferenceVariable> result = new HashSet<InferenceVariable>();
            final PsiSubstitutor substitutor = LambdaUtil.getSubstitutor(interfaceMethod, resolveResult);
            if (psiExpression instanceof PsiLambdaExpression && !((PsiLambdaExpression)psiExpression).hasFormalParameterTypes() || 
                psiExpression instanceof PsiMethodReferenceExpression && !((PsiMethodReferenceExpression)psiExpression).isExact()) {
              for (PsiParameter parameter : interfaceMethod.getParameterList().getParameters()) {
                session.collectDependencies(substitutor.substitute(parameter.getType()), result, true);
              }
            }

            collectReturnTypeVariables(session, psiExpression, substitutor.substitute(interfaceMethod.getReturnType()), result);

            return result;
          }
        }
      }

      if (psiExpression instanceof PsiParenthesizedExpression) {
        final PsiExpression expression = ((PsiParenthesizedExpression)psiExpression).getExpression();
        return expression != null ? createSelfConstraint(type, expression).getInputVariables(session) : null;
      }

      if (psiExpression instanceof PsiConditionalExpression) {
        final PsiExpression thenExpression = ((PsiConditionalExpression)psiExpression).getThenExpression();
        final PsiExpression elseExpression = ((PsiConditionalExpression)psiExpression).getElseExpression();
        final Set<InferenceVariable> thenResult = thenExpression != null ? createSelfConstraint(type, thenExpression).getInputVariables(session) : null;
        final Set<InferenceVariable> elseResult = elseExpression != null ? createSelfConstraint(type, elseExpression).getInputVariables(session) : null;
        if (thenResult == null) {
          return elseResult;
        } else if (elseResult == null) {
          return thenResult;
        } else {
          thenResult.addAll(elseResult);
          return thenResult;
        }
      }
    }
    return null;
  }


  @Nullable
  public Set<InferenceVariable> getOutputVariables(Set<InferenceVariable> inputVariables, InferenceSession session) {
    if (PsiPolyExpressionUtil.isPolyExpression(getExpression())) {
      final HashSet<InferenceVariable> mentionedVariables = new HashSet<InferenceVariable>();
      session.collectDependencies(getT(), mentionedVariables, true);
      if (inputVariables != null) {
        mentionedVariables.removeAll(inputVariables);
      }
      return mentionedVariables;
    }
    return null;
  }

  @Override
  public void apply(PsiSubstitutor substitutor) {
    setT(substitutor.substitute(getT()));
  }
}
