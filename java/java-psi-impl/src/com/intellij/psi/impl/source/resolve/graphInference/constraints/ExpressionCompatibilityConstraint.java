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
import com.intellij.psi.impl.source.resolve.graphInference.InferenceSession;
import com.intellij.psi.impl.source.resolve.graphInference.PsiPolyExpressionUtil;
import com.intellij.psi.impl.source.tree.java.PsiMethodCallExpressionImpl;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * User: anna
 */
public class ExpressionCompatibilityConstraint implements ConstraintFormula {
  private PsiExpression myExpression;
  private PsiType myT;

  public ExpressionCompatibilityConstraint(@NotNull PsiExpression expression, @NotNull PsiType type) {
    myExpression = expression;
    myT = type;
  }

  @Override
  public boolean reduce(InferenceSession session, List<ConstraintFormula> constraints, List<ConstraintFormula> delayedConstraints) {
    if (session.isProperType(myT)) {
      return TypeConversionUtil.areTypesAssignmentCompatible(myT, myExpression);
    }
    if (!PsiPolyExpressionUtil.isPolyExpression(myExpression)) {
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
      final JavaResolveResult resolveResult = ((PsiCallExpression)myExpression).resolveMethodGenerics();
      final PsiMethod method = (PsiMethod)resolveResult.getElement();
      if (method != null) {
        final PsiTypeParameter[] typeParameters = method.getTypeParameters();
        if (typeParameters.length == 0) {
          final PsiType exprType = myExpression.getType();
          if (exprType != null && !exprType.equals(PsiType.NULL)) {
            constraints.add(new TypeCompatibilityConstraint(myT, exprType));
          }
        } else {
          final PsiExpressionList argumentList = ((PsiCallExpression)myExpression).getArgumentList();
          PsiType returnType = method.getReturnType();
          if (argumentList != null && returnType != null) { //todo constructor
            final InferenceSession callSession = new InferenceSession(typeParameters, method.getParameterList().getParameters(),
                                                                      argumentList.getExpressions(),
                                                                      PsiSubstitutor.EMPTY, null, myExpression.getManager());
            for (PsiTypeParameter typeParameter : session.getTypeParams()) {
              callSession.addCapturedVariable(typeParameter);
            }
            callSession.addConstraint(new TypeCompatibilityConstraint(myT, returnType));
            final PsiSubstitutor callSubstitutor = callSession.infer();
            if (myExpression instanceof PsiMethodCallExpression) {
              returnType =
                PsiMethodCallExpressionImpl.captureReturnType((PsiMethodCallExpression)myExpression, method, returnType, callSubstitutor);
            } else {
              returnType = callSubstitutor.substitute(returnType);
            }
            constraints.add(new TypeCompatibilityConstraint(myT, returnType));  //todo primitive types
          }
        }
      }
      return true;
    }
    
    if (myExpression instanceof PsiMethodReferenceExpression) {
      //todo
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
}
