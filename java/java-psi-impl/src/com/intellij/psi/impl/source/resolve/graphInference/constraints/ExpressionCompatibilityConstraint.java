/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.codeInsight.daemon.impl.analysis.JavaGenericsUtil;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.graphInference.InferenceSession;
import com.intellij.psi.impl.source.resolve.graphInference.InferenceVariable;
import com.intellij.psi.impl.source.resolve.graphInference.PsiPolyExpressionUtil;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

public class ExpressionCompatibilityConstraint extends InputOutputConstraintFormula {
  private final PsiExpression myExpression;
  private PsiType myT;

  public ExpressionCompatibilityConstraint(@NotNull PsiExpression expression, @NotNull PsiType type) {
    myExpression = expression;
    myT = type;
  }

  @Override
  public boolean reduce(InferenceSession session, List<ConstraintFormula> constraints) {
    if (!PsiPolyExpressionUtil.isPolyExpression(myExpression)) {

      PsiType exprType = myExpression.getType();

      if (session.isProperType(myT)) {
        final boolean assignmentCompatible = exprType == null || TypeConversionUtil.isAssignable(myT, exprType);
        if (!assignmentCompatible) {
          final PsiType type = myExpression.getType();
          session.registerIncompatibleErrorMessage((type != null ? type.getPresentableText() : myExpression.getText()) + " is not compatible with " + session.getPresentableText(myT));
        }
        else if (TypeCompatibilityConstraint.isUncheckedConversion(myT, exprType) && !JavaGenericsUtil.isReifiableType(myT)) {
          session.setErased();
        }
        return assignmentCompatible;
      }

      if (exprType instanceof PsiLambdaParameterType) {
        return false;
      }

      if (exprType instanceof PsiClassType) {
        if (((PsiClassType)exprType).resolve() == null) {
          return true;
        }
      }

      if (exprType != null && exprType != PsiType.NULL) {
        if (exprType instanceof PsiDisjunctionType) {
          exprType = ((PsiDisjunctionType)exprType).getLeastUpperBound();
        }

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

    if (myExpression instanceof PsiCall) {
      final InferenceSession callSession = reduceExpressionCompatibilityConstraint(session, myExpression, myT, true);
      if (callSession == null) {
        return false;
      }
      if (callSession != session) {
        session.getInferenceSessionContainer().registerNestedSession(callSession);
        session.propagateVariables(callSession.getInferenceVariables(), callSession.getRestoreNameSubstitution());
        for (Pair<InferenceVariable[], PsiClassType> pair : callSession.myIncorporationPhase.getCaptures()) {
          session.myIncorporationPhase.addCapture(pair.first, pair.second);
        }
        callSession.setUncheckedInContext();
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

  public static InferenceSession reduceExpressionCompatibilityConstraint(InferenceSession session,
                                                                         PsiExpression expression,
                                                                         PsiType targetType,
                                                                         boolean registerErrorOnFailure) {
    if (!PsiPolyExpressionUtil.isPolyExpression(expression)) {
      return session;
    }
    final PsiExpressionList argumentList = ((PsiCall)expression).getArgumentList();
    if (argumentList != null) {
      final MethodCandidateInfo.CurrentCandidateProperties candidateProperties = MethodCandidateInfo.getCurrentMethod(argumentList);
      PsiType returnType = null;
      PsiTypeParameter[] typeParams = null;
      final JavaResolveResult resolveResult = candidateProperties != null ? null : PsiDiamondType
        .getDiamondsAwareResolveResult((PsiCall)expression);
      final PsiMethod method = InferenceSession.getCalledMethod((PsiCall)expression);

      if (method != null && !method.isConstructor()) {
        returnType = method.getReturnType();
        typeParams = method.getTypeParameters();
      }
      else if (resolveResult != null) {
        final PsiClass psiClass = method != null ? method.getContainingClass() : (PsiClass)resolveResult.getElement();
        if (psiClass != null) {
          returnType = JavaPsiFacade.getElementFactory(argumentList.getProject()).createType(psiClass, PsiSubstitutor.EMPTY);
          typeParams = psiClass.getTypeParameters();
          if (method != null && method.hasTypeParameters()) {
            typeParams = ArrayUtil.mergeArrays(typeParams, method.getTypeParameters());
          }
        }
      }
      else {
        return session;
      }

      if (typeParams != null) {
        PsiSubstitutor siteSubstitutor = InferenceSession.chooseSiteSubstitutor(candidateProperties, resolveResult, method);
        final InferenceSession callSession = new InferenceSession(typeParams, siteSubstitutor, expression.getManager(), expression);
        callSession.propagateVariables(session.getInferenceVariables(), session.getRestoreNameSubstitution());
        if (method != null) {
          final PsiExpression[] args = argumentList.getExpressions();
          final PsiParameter[] parameters = method.getParameterList().getParameters();
          callSession.initExpressionConstraints(parameters, args, expression, method, InferenceSession
            .chooseVarargsMode(candidateProperties, resolveResult));
        }
        if (callSession.repeatInferencePhases()) {

          if (PsiType.VOID.equals(targetType)) {
            return callSession;
          }

          if (returnType != null) {
            callSession.registerReturnTypeConstraints(siteSubstitutor.substitute(returnType), targetType, expression);
          }
          if (callSession.repeatInferencePhases()) {
            return callSession;
          }
        }

        //copy incompatible message if any
        final List<String> messages = callSession.getIncompatibleErrorMessages();
        if (messages != null) {
          for (String message : messages) {
            session.registerIncompatibleErrorMessage(message);
          }
        }
        return null;
      }
      else if (registerErrorOnFailure) {
        session.registerIncompatibleErrorMessage("Failed to resolve argument");
        return null;
      }
    }
    return session;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ExpressionCompatibilityConstraint that = (ExpressionCompatibilityConstraint)o;

    if (!myExpression.equals(that.myExpression)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myExpression.hashCode();
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
      if (!PsiType.VOID.equals(returnType)) {
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
