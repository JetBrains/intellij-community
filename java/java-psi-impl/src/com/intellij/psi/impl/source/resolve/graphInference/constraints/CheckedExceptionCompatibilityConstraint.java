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

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.graphInference.InferenceSession;
import com.intellij.psi.impl.source.resolve.graphInference.InferenceVariable;
import com.intellij.psi.impl.source.resolve.graphInference.PsiPolyExpressionUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * User: anna
 */
public class CheckedExceptionCompatibilityConstraint extends InputOutputConstraintFormula {
  private static final Logger LOG = Logger.getInstance("#" + CheckedExceptionCompatibilityConstraint.class.getName());
  private final PsiExpression myExpression;
  private PsiType myT;

  public CheckedExceptionCompatibilityConstraint(PsiExpression expression, PsiType t) {
    myExpression = expression;
    myT = t;
  }

  @Override
  public boolean reduce(InferenceSession session, List<ConstraintFormula> constraints) {
    if (!PsiPolyExpressionUtil.isPolyExpression(myExpression) ||
        myExpression instanceof PsiCallExpression) {
      return true;
    }
    if (myExpression instanceof PsiParenthesizedExpression) {
      constraints.add(new CheckedExceptionCompatibilityConstraint(((PsiParenthesizedExpression)myExpression).getExpression(), myT));
      return true;
    }
    if (myExpression instanceof PsiConditionalExpression) {
      final PsiExpression thenExpression = ((PsiConditionalExpression)myExpression).getThenExpression();
      if (thenExpression != null) {
        constraints.add(new CheckedExceptionCompatibilityConstraint(thenExpression, myT));
      }
      final PsiExpression elseExpression = ((PsiConditionalExpression)myExpression).getElseExpression();
      if (elseExpression != null) {
        constraints.add(new CheckedExceptionCompatibilityConstraint(elseExpression, myT));
      }
      return true;
    }
    if (myExpression instanceof PsiLambdaExpression || myExpression instanceof PsiMethodReferenceExpression) {
      if (LambdaHighlightingUtil.checkInterfaceFunctional(myT) != null) {
        return false;
      }
      final PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(myT);
      if (interfaceMethod == null) {
        return false;
      }

      final PsiSubstitutor substitutor = LambdaUtil.getSubstitutor(interfaceMethod, PsiUtil.resolveGenericsClassInType(myT));
      for (PsiParameter parameter : interfaceMethod.getParameterList().getParameters()) {
        if (!session.isProperType(substitutor.substitute(parameter.getType()))) return false;
      }
      final PsiType returnType = interfaceMethod.getReturnType();
      LOG.assertTrue(returnType != null, interfaceMethod);
      if (!session.isProperType(substitutor.substitute(returnType))) return false;

      final List<PsiType>
        expectedThrownTypes = ContainerUtil.map(interfaceMethod.getThrowsList().getReferencedTypes(), new Function<PsiType, PsiType>() {
        @Override
        public PsiType fun(PsiType type) {
          return substitutor.substitute(type);
        }
      });
      final List<PsiType> expectedNonProperThrownTypes = new ArrayList<PsiType>();
      for (PsiType type : expectedThrownTypes) {
        if (!session.isProperType(type)) {
          expectedNonProperThrownTypes.add(type);
        }
      }
      
      final List<PsiType> thrownTypes = new ArrayList<PsiType>();
      if (myExpression instanceof PsiLambdaExpression) {
        //todo
      } else {
        final PsiElement resolve = ((PsiMethodReferenceExpression)myExpression).resolve();
        if (resolve instanceof PsiMethod) {
          for (PsiClassType type : ((PsiMethod)resolve).getThrowsList().getReferencedTypes()) {
            if (!ExceptionUtil.isUncheckedException(type)) {
              thrownTypes.add(type);
            }
          }
        }
      }
      
      if (expectedNonProperThrownTypes.isEmpty()) {
        for (PsiType thrownType : thrownTypes) {
          if (!isAddressed(expectedThrownTypes, thrownType)) return false;
        }
      } else {
        final ArrayList<PsiType> expectedProperTypes = new ArrayList<PsiType>(expectedThrownTypes);
        expectedProperTypes.retainAll(expectedNonProperThrownTypes);
        for (PsiType thrownType : thrownTypes) {
          if (!isAddressed(expectedProperTypes, thrownType)) {
            for (PsiType expectedNonProperThrownType : expectedNonProperThrownTypes) {
              constraints.add(new TypeCompatibilityConstraint(expectedNonProperThrownType, thrownType));
            }
          }
        }
      }
    }

    return true;
  }

  private static boolean isAddressed(List<PsiType> expectedThrownTypes, PsiType thrownType) {
    for (PsiType expectedThrownType : expectedThrownTypes) {
      if (TypeConversionUtil.isAssignable(expectedThrownType, thrownType)) {
        return true;
      }
    }
    return false;
  }

  @Override
  protected PsiExpression getExpression() {
    return myExpression;
  }

  @Override
  protected PsiType getT() {
    return myT;
  }

  @Override
  protected void setT(PsiType t) {
    myT = t;
  }

  @Override
  protected InputOutputConstraintFormula createSelfConstraint(PsiType type, PsiExpression expression) {
    return new CheckedExceptionCompatibilityConstraint(expression, type);
  }

  @Override
  protected void collectReturnTypeVariables(InferenceSession session,
                                            PsiExpression psiExpression,
                                            PsiType returnType, 
                                            Set<InferenceVariable> result) {
    session.collectDependencies(returnType, result, true);
  }
}
