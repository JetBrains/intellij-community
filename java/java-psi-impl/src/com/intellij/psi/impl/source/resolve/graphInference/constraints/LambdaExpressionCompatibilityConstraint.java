// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.resolve.graphInference.constraints;

import com.intellij.core.JavaPsiBundle;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.graphInference.FunctionalInterfaceParameterizationUtil;
import com.intellij.psi.impl.source.resolve.graphInference.InferenceSession;
import com.intellij.psi.impl.source.resolve.graphInference.InferenceVariable;
import com.intellij.psi.impl.source.resolve.graphInference.PsiPolyExpressionUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;

import java.util.List;

public class LambdaExpressionCompatibilityConstraint implements ConstraintFormula {
  private final PsiLambdaExpression myExpression;
  private PsiType myT;

  public LambdaExpressionCompatibilityConstraint(PsiLambdaExpression expression, PsiType t) {
    myExpression = expression;
    myT = t;
  }

  @Override
  public boolean reduce(InferenceSession session, List<? super ConstraintFormula> constraints) {
    if (!LambdaUtil.isFunctionalType(myT)) {
      session.registerIncompatibleErrorMessage(
        JavaPsiBundle.message("error.incompatible.type.not.a.functional.interface", session.getPresentableText(myT)));
      return false;
    }

    final PsiType groundTargetType = FunctionalInterfaceParameterizationUtil.getGroundTargetType(myT, myExpression, false);
    final PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(groundTargetType);
    final PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(resolveResult);
    if (interfaceMethod == null) {
      session.registerIncompatibleErrorMessage(
        JavaPsiBundle.message("error.incompatible.type.no.valid.function.type.found", session.getPresentableText(myT)));
      return false;
    }
    final PsiSubstitutor substitutor = LambdaUtil.getSubstitutor(interfaceMethod, resolveResult);
    final PsiParameter[] parameters = interfaceMethod.getParameterList().getParameters();

    final PsiParameter[] lambdaParameters = myExpression.getParameterList().getParameters();
    if (lambdaParameters.length != parameters.length) {
      session.registerIncompatibleErrorMessage(JavaPsiBundle.message("error.incompatible.type.incompatible.parameter.types.in.lambda", parameters.length, lambdaParameters.length));
      return false;
    }
    if (myExpression.hasFormalParameterTypes()) {
      for (int i = 0; i < lambdaParameters.length; i++) {
        constraints.add(new TypeEqualityConstraint(lambdaParameters[i].getType(), session.substituteWithInferenceVariables(substitutor.substitute(parameters[i].getType()))));
      }
      constraints.add(new StrictSubtypingConstraint(myT, groundTargetType));
    }
    else {
      for (PsiParameter parameter : parameters) {
        final PsiType type = session.substituteWithInferenceVariables(substitutor.substitute(parameter.getType()));
        if (!session.isProperType(type)) {
          //session.registerIncompatibleErrorMessage("Parameter type in not yet inferred: " + session.getPresentableText(type));
          return false;
        }
      }
    }

    PsiType returnType = interfaceMethod.getReturnType();
    if (returnType != null) {
      final List<PsiExpression> returnExpressions = LambdaUtil.getReturnExpressions(myExpression);
      final PsiElement lambdaBody = myExpression.getBody();
      if (returnType.equals(PsiType.VOID)) {
        if (!(lambdaBody instanceof PsiCodeBlock && myExpression.isVoidCompatible()) && !LambdaUtil.isExpressionStatementExpression(lambdaBody)) {
          session.registerIncompatibleErrorMessage(JavaPsiBundle.message("error.incompatible.type.incompatible.types.expected.void.lambda"));
          return false;
        }
      }
      else {
        if (lambdaBody instanceof PsiCodeBlock && !myExpression.isValueCompatible()) {
          session.registerIncompatibleErrorMessage(JavaPsiBundle.message("error.incompatible.type.expected.value.lambda"));
          return false;
        }
        final PsiSubstitutor nestedSubstitutor = session.getInferenceSessionContainer().findNestedSubstitutor(myExpression, session.getInferenceSubstitution());
        returnType = nestedSubstitutor.substitute(substitutor.substitute(returnType));
        boolean isProperType = InferenceSession.collectDependencies(returnType, null, type -> {
          final PsiClass psiClass = PsiUtil.resolveClassInClassTypeOnly(type);
          return psiClass instanceof InferenceVariable && nestedSubstitutor.getSubstitutionMap().containsValue(type) ? (InferenceVariable)psiClass : null;
        });
        if (!isProperType || myExpression.hasFormalParameterTypes()) {
          for (PsiExpression returnExpression : returnExpressions) {
            if (!InferenceSession.ignoreLambdaConstraintTree(returnExpression)) {
              constraints.add(new ExpressionCompatibilityConstraint(returnExpression, returnType));
            }
          }
        }
        else {
          for (PsiExpression returnExpression : returnExpressions) {
            if (!PsiPolyExpressionUtil.isPolyExpression(returnExpression)) {
              if (!TypeConversionUtil.areTypesAssignmentCompatible(returnType, returnExpression)) {
                final PsiType type = returnExpression.getType();
                if (type != null) {
                  session.registerIncompatibleErrorMessage(
                    JavaPsiBundle.message("error.incompatible.type.bad.lambda.return.type", session.getPresentableText(type), session.getPresentableText(returnType)));
                }
                else {
                  session.registerIncompatibleErrorMessage(
                    JavaPsiBundle.message("error.incompatible.type", returnExpression.getText(), session.getPresentableText(returnType)));
                }
                return false;
              }
            }
            else {
              //todo check compatibility
            }
          }
        }
      }
    }
    return true;
  }

  @Override
  public void apply(PsiSubstitutor substitutor, boolean cache) {
    myT = substitutor.substitute(myT);
  }

  @Override
  public String toString() {
    return myExpression.getText() + " -> " + myT.getPresentableText();
  }
}
