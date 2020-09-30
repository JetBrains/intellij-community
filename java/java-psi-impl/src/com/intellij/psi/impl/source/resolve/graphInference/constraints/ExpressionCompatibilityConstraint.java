// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.resolve.graphInference.constraints;

import com.intellij.codeInsight.daemon.impl.analysis.JavaGenericsUtil;
import com.intellij.core.JavaPsiBundle;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.graphInference.InferenceSession;
import com.intellij.psi.impl.source.resolve.graphInference.InferenceVariable;
import com.intellij.psi.impl.source.resolve.graphInference.PsiPolyExpressionUtil;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

public class ExpressionCompatibilityConstraint extends InputOutputConstraintFormula {
  private final PsiExpression myExpression;

  public ExpressionCompatibilityConstraint(@NotNull PsiExpression expression, @NotNull PsiType type) {
    super(type);
    myExpression = expression;
  }

  @Override
  public boolean reduce(InferenceSession session, List<? super ConstraintFormula> constraints) {
    PsiType myT = getCurrentType();
    if (!PsiPolyExpressionUtil.isPolyExpression(myExpression)) {

      PsiType exprType = myExpression.getType();

      if (session.isProperType(myT)) {
        final boolean assignmentCompatible = exprType == null || TypeConversionUtil.isAssignable(myT, exprType);
        if (!assignmentCompatible) {
          final PsiType type = myExpression.getType();
          session.registerIncompatibleErrorMessage(
            JavaPsiBundle.message("error.incompatible.type", type != null ? type.getPresentableText() : myExpression.getText(), session.getPresentableText(myT)));
        }
        else if (TypeCompatibilityConstraint.isUncheckedConversion(myT, exprType, session) && !JavaGenericsUtil.isReifiableType(myT)) {
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
      if (expression != null && !InferenceSession.ignoreLambdaConstraintTree(expression)) {
        constraints.add(new ExpressionCompatibilityConstraint(expression, myT));
        return true;
      }
    }

    if (myExpression instanceof PsiConditionalExpression) {
      final PsiExpression thenExpression = ((PsiConditionalExpression)myExpression).getThenExpression();
      if (thenExpression != null && !InferenceSession.ignoreLambdaConstraintTree(thenExpression)) {
        constraints.add(new ExpressionCompatibilityConstraint(thenExpression, myT));
      }

      final PsiExpression elseExpression = ((PsiConditionalExpression)myExpression).getElseExpression();
      if (elseExpression != null && !InferenceSession.ignoreLambdaConstraintTree(elseExpression)) {
        constraints.add(new ExpressionCompatibilityConstraint(elseExpression, myT));
      }
      return true;
    }

    if (myExpression instanceof PsiSwitchExpression) {
      PsiUtil.getSwitchResultExpressions((PsiSwitchExpression)myExpression).forEach(expression -> {
        if (!InferenceSession.ignoreLambdaConstraintTree(expression)) {
          constraints.add(new ExpressionCompatibilityConstraint(expression, myT));
        }
      });
      return true;
    }

    if (myExpression instanceof PsiCall) {
      final InferenceSession callSession = reduceExpressionCompatibilityConstraint(session, myExpression, myT, true);
      if (callSession == null) {
        return false;
      }
      if (callSession != session) {
        session.getInferenceSessionContainer().registerNestedSession(callSession);
        session.propagateVariables(callSession);
        for (Pair<InferenceVariable[], PsiClassType> pair : callSession.myIncorporationPhase.getCaptures()) {
          session.myIncorporationPhase.addCapture(pair.first, pair.second);
        }
        final MethodCandidateInfo currentMethod = session.getCurrentMethod(((PsiCall)myExpression).getArgumentList());
        final JavaResolveResult resolveResult = currentMethod != null ? currentMethod : PsiDiamondType.getDiamondsAwareResolveResult((PsiCall)myExpression);
        if (resolveResult instanceof MethodCandidateInfo && callSession.isErased()) {
          ((MethodCandidateInfo)resolveResult).setErased();
        }
      }
      return true;
    }

    if (myExpression instanceof PsiMethodReferenceExpression) {
      constraints.add(new PsiMethodReferenceCompatibilityConstraint((PsiMethodReferenceExpression)myExpression, myT));
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
      final MethodCandidateInfo currentMethod = session.getCurrentMethod(argumentList);
      PsiType returnType = null;
      PsiTypeParameter[] typeParams = null;
      final JavaResolveResult resolveResult = currentMethod != null ? null : PsiDiamondType.getDiamondsAwareResolveResult((PsiCall)expression);
      PsiMethod method = currentMethod != null ? currentMethod.getElement() :
                         resolveResult instanceof MethodCandidateInfo ? ((MethodCandidateInfo)resolveResult).getElement() :
                         null;

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
        PsiSubstitutor siteSubstitutor = InferenceSession.chooseSiteSubstitutor(currentMethod, resolveResult, method);
        InferenceSession callSession = new InferenceSession(typeParams, siteSubstitutor, expression.getManager(), expression, session.getInferencePolicy());
        callSession.propagateVariables(session);
        if (method != null) {
          final PsiExpression[] args = argumentList.getExpressions();
          final PsiParameter[] parameters = method.getParameterList().getParameters();
          callSession.initExpressionConstraints(parameters, args, method, InferenceSession.chooseVarargsMode(currentMethod, resolveResult));
        }
        if (callSession.repeatInferencePhases()) {

          if (PsiType.VOID.equals(targetType)) {
            return callSession;
          }

          if (returnType != null) {
            callSession.registerReturnTypeConstraints(siteSubstitutor.substitute(returnType), targetType, expression);
          }
          if (callSession.repeatInferencePhases()) {
            if (callSession.isErased() &&
                !JavaGenericsUtil.isReifiableType(targetType) && session.getInferenceVariable(targetType) == null) {
              session.setErased();
            }
            return callSession;
          }
        }

        //copy incompatible message if any
        final List<@Nls String> messages = callSession.getIncompatibleErrorMessages();
        if (messages != null) {
          for (@Nls String message : messages) {
            session.registerIncompatibleErrorMessage(message);
          }
        }
        return null;
      }
      else if (registerErrorOnFailure) {
        //keep a sign that an inference failed
        session.registerIncompatibleErrorMessage(JavaPsiBundle.message("error.incompatible.type.failed.to.resolve.argument"));
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
  protected InputOutputConstraintFormula createSelfConstraint(PsiType type, PsiExpression expression) {
    return new ExpressionCompatibilityConstraint(expression, type);
  }

  @Override
  protected void collectReturnTypeVariables(InferenceSession session,
                                            PsiExpression psiExpression,
                                            PsiType returnType,
                                            Set<? super InferenceVariable> result) {
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
