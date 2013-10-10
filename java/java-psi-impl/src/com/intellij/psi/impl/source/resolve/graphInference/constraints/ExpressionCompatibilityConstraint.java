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

import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.graphInference.InferenceSession;
import com.intellij.psi.impl.source.resolve.graphInference.InferenceVariable;
import com.intellij.psi.impl.source.resolve.graphInference.PsiPolyExpressionUtil;
import com.intellij.psi.impl.source.tree.java.PsiMethodCallExpressionImpl;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * User: anna
 */
public class ExpressionCompatibilityConstraint extends InputOutputConstraintFormula {
  private PsiExpression myExpression;
  private PsiType myT;

  public ExpressionCompatibilityConstraint(@NotNull PsiExpression expression, @NotNull PsiType type) {
    myExpression = expression;
    myT = type;
  }

  @Override
  public boolean reduce(InferenceSession session, List<ConstraintFormula> constraints) {
    if (!PsiPolyExpressionUtil.isPolyExpression(myExpression)) {
      if (session.isProperType(myT)) {
        return TypeConversionUtil.areTypesAssignmentCompatible(myT, myExpression);
      }
    
      final PsiType exprType = myExpression.getType();
      if (exprType != null && !exprType.equals(PsiType.NULL)) {
        constraints.add(new TypeCompatibilityConstraint(myT, exprType));
      }
      return true;
    }
    if (myExpression instanceof PsiParenthesizedExpression) {
      final PsiExpression expression = ((PsiParenthesizedExpression)myExpression).getExpression();
      if (expression != null) {
        constraints.add(new ExpressionCompatibilityConstraint(expression, myT));
        return true;
      }
    }
    
    if (myExpression instanceof PsiConditionalExpression) {
      final PsiExpression thenExpression = ((PsiConditionalExpression)myExpression).getThenExpression();
      if (thenExpression != null) {
        constraints.add(new ExpressionCompatibilityConstraint(thenExpression, myT));
      }

      final PsiExpression elseExpression = ((PsiConditionalExpression)myExpression).getElseExpression();
      if (elseExpression != null) {
        constraints.add(new ExpressionCompatibilityConstraint(elseExpression, myT));
      }
      return true;
    }
    
    if (myExpression instanceof PsiCallExpression) {
      final PsiExpressionList argumentList = ((PsiCallExpression)myExpression).getArgumentList();
      if (argumentList != null) {
        final Pair<PsiMethod,PsiSubstitutor> pair = MethodCandidateInfo.getCurrentMethod(argumentList);
        if (pair != null) return true;
        final PsiMethod method = ((PsiCallExpression)myExpression).resolveMethod();
        PsiType returnType = null;
        InferenceSession callSession = null;
        if (method != null) {
          returnType = method.getReturnType();
          final PsiParameter[] parameters = method.getParameterList().getParameters();
          if (returnType != null) {
            callSession = new InferenceSession(method.getTypeParameters(), parameters,
                                               argumentList.getExpressions(),
                                               PsiSubstitutor.EMPTY, null, myExpression.getManager());
            
          }
        } else if (myExpression instanceof PsiNewExpression) {  //default constructor
          final PsiJavaCodeReferenceElement classReference = ((PsiNewExpression)myExpression).getClassOrAnonymousClassReference();
          if (classReference != null) {
            final PsiElement psiClass = classReference.resolve();
            if (psiClass instanceof PsiClass) {
              returnType = JavaPsiFacade.getElementFactory(argumentList.getProject()).createType((PsiClass)psiClass, PsiSubstitutor.EMPTY);
              callSession = new InferenceSession(((PsiClass)psiClass).getTypeParameters(),
                                                 PsiParameter.EMPTY_ARRAY,
                                                 argumentList.getExpressions(),
                                                 PsiSubstitutor.EMPTY, null, myExpression.getManager());
            }
          }
        }

        if (callSession != null) {

          for (PsiTypeParameter typeParameter : session.getTypeParams()) {
            callSession.addCapturedVariable(typeParameter);
          }
          callSession.addConstraint(new TypeCompatibilityConstraint(myT, returnType));
          final PsiSubstitutor callSubstitutor = callSession.infer();

          if (myExpression instanceof PsiMethodCallExpression) {
            returnType = PsiMethodCallExpressionImpl.captureReturnType((PsiMethodCallExpression)myExpression, method, returnType, callSubstitutor);
          }
          else {
            returnType = callSubstitutor.substitute(returnType);
          }
          constraints.add(new TypeCompatibilityConstraint(GenericsUtil.eliminateWildcards(myT, false), returnType));
        }
      }
      return true;
    }
    
    if (myExpression instanceof PsiMethodReferenceExpression) {
      constraints.add(new PsiMethodReferenceCompatibilityConstraint(((PsiMethodReferenceExpression)myExpression), myT));
      return true;
    }
    
    if (myExpression instanceof PsiLambdaExpression) {
      constraints.add(new LambdaExpressionCompatibilityConstraint((PsiLambdaExpression)myExpression, myT));
      return true;
    }
    
    
    return true;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ExpressionCompatibilityConstraint that = (ExpressionCompatibilityConstraint)o;

    if (!myExpression.equals(that.myExpression)) return false;
    if (!myT.equals(that.myT)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myExpression.hashCode();
    result = 31 * result + myT.hashCode();
    return result;
  }

  @Override
  public PsiExpression getExpression() {
    return myExpression;
  }

  @Override
  public PsiType getT() {
    return myT;
  }

  @Override
  protected void setT(PsiType t) {
    myT = t;
  }

  @Override
  protected InputOutputConstraintFormula createSelfConstraint(PsiType type, PsiExpression expression) {
    return new ExpressionCompatibilityConstraint(expression, type);
  }

  protected void collectReturnTypeVariables(InferenceSession session,
                                            PsiExpression psiExpression,
                                            PsiType returnType, 
                                            Set<InferenceVariable> result) {
    if (psiExpression instanceof PsiLambdaExpression) {
      if (returnType != PsiType.VOID) {
        final List<PsiExpression> returnExpressions = LambdaUtil.getReturnExpressions((PsiLambdaExpression)psiExpression);
        for (PsiExpression expression : returnExpressions) {
          final Set<InferenceVariable> resultInputVars = createSelfConstraint(returnType, expression).getInputVariables(session);
          if (resultInputVars != null) {
            result.addAll(resultInputVars);
          }
        }
      }
    }
  }
}
