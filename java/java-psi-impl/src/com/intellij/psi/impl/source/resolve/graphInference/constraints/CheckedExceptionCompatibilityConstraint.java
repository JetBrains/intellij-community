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
import com.intellij.openapi.util.Condition;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.graphInference.FunctionalInterfaceParameterizationUtil;
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
  public boolean reduce(final InferenceSession session, List<ConstraintFormula> constraints) {
    if (!PsiPolyExpressionUtil.isPolyExpression(myExpression)) {
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
      if (!LambdaUtil.isFunctionalType(myT)) {
        session.registerIncompatibleErrorMessage(session.getPresentableText(myT) + " is not a functional interface");
        return false;
      }

      final PsiType groundTargetType = myExpression instanceof PsiLambdaExpression ? FunctionalInterfaceParameterizationUtil.getGroundTargetType(myT, (PsiLambdaExpression)myExpression, false) 
                                                                                   : FunctionalInterfaceParameterizationUtil.getGroundTargetType(myT);
      final PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(groundTargetType);
      if (interfaceMethod == null) {
        session.registerIncompatibleErrorMessage("No valid function type can be found for " + session.getPresentableText(myT));
        return false;
      }

      final PsiSubstitutor substitutor = LambdaUtil.getSubstitutor(interfaceMethod, PsiUtil.resolveGenericsClassInType(myT));
      if (myExpression instanceof PsiLambdaExpression && !((PsiLambdaExpression)myExpression).hasFormalParameterTypes() ||
          myExpression instanceof PsiMethodReferenceExpression && !((PsiMethodReferenceExpression)myExpression).isExact()) {
        for (PsiParameter parameter : interfaceMethod.getParameterList().getParameters()) {
          final PsiType type = session.substituteWithInferenceVariables(substitutor.substitute(parameter.getType()));
          if (!session.isProperType(type)) {
            session.registerIncompatibleErrorMessage("Parameter type is not yet inferred: " + session.getPresentableText(type));
            return false;
          }
        }
      }

      final PsiType returnType = interfaceMethod.getReturnType();
      if (myExpression instanceof PsiLambdaExpression || !((PsiMethodReferenceExpression)myExpression).isExact()) {
        final PsiType type = session.substituteWithInferenceVariables(substitutor.substitute(returnType));
        if (!session.isProperType(type)) {
          session.registerIncompatibleErrorMessage("Return type is not yet inferred: " + session.getPresentableText(type));
          return false;
        }
      }

      final List<PsiType>
        expectedThrownTypes = ContainerUtil.map(interfaceMethod.getThrowsList().getReferencedTypes(), new Function<PsiType, PsiType>() {
        @Override
        public PsiType fun(PsiType type) {
          return session.substituteWithInferenceVariables(substitutor.substitute(type));
        }
      });
      final List<PsiType> expectedNonProperThrownTypes = new ArrayList<PsiType>();
      for (PsiType type : expectedThrownTypes) {
        if (!session.isProperType(type)) {
          expectedNonProperThrownTypes.add(type);
        }
      }
      
      final List<PsiType> thrownTypes = new ArrayList<PsiType>();
      final PsiElement body = myExpression instanceof PsiLambdaExpression ? ((PsiLambdaExpression)myExpression).getBody() : myExpression;
      if (body != null) {
        final List<PsiClassType> exceptions =  ExceptionUtil.getUnhandledExceptions(new PsiElement[] {body});
        if (exceptions != null) {
          thrownTypes.addAll(ContainerUtil.filter(exceptions, new Condition<PsiClassType>() {
            @Override
            public boolean value(PsiClassType type) {
              return !ExceptionUtil.isUncheckedException(type);
            }
          }));
        }
      }

      if (expectedNonProperThrownTypes.isEmpty()) {
        for (PsiType thrownType : thrownTypes) {
          if (!isAddressed(expectedThrownTypes, thrownType)) {
            session.registerIncompatibleErrorMessage("Unhandled exception: " + session.getPresentableText(thrownType));
            return false;
          }
        }
      } else {
        final ArrayList<PsiType> expectedProperTypes = new ArrayList<PsiType>(expectedThrownTypes);
        expectedProperTypes.removeAll(expectedNonProperThrownTypes);
        for (PsiType thrownType : thrownTypes) {
          if (!isAddressed(expectedProperTypes, thrownType)) {
            for (PsiType expectedNonProperThrownType : expectedNonProperThrownTypes) {
              constraints.add(new StrictSubtypingConstraint(expectedNonProperThrownType, thrownType));
            }
          }
        }

        for (PsiType expectedNonProperThrownType : expectedNonProperThrownTypes) {
          final InferenceVariable variable = session.getInferenceVariable(expectedNonProperThrownType);
          //could be null for invalid code
          if (variable != null) {
            variable.setThrownBound();
          }
        }
      }
    }

    return true;
  }

  private static boolean isAddressed(List<PsiType> expectedThrownTypes, PsiType thrownType) {
    for (PsiType expectedThrownType : expectedThrownTypes) {
      if (TypeConversionUtil.isAssignable(TypeConversionUtil.erasure(thrownType), expectedThrownType)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public PsiExpression getExpression() {
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
    session.collectDependencies(returnType, result);
  }
}
