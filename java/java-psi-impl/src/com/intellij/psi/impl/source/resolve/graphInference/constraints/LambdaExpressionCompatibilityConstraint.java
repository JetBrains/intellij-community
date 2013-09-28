package com.intellij.psi.impl.source.resolve.graphInference.constraints;

import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.graphInference.FunctionalInterfaceParameterizationUtil;
import com.intellij.psi.impl.source.resolve.graphInference.InferenceSession;
import com.intellij.psi.util.PsiUtil;

import java.util.List;

/**
 * User: anna
 */
public class LambdaExpressionCompatibilityConstraint implements ConstraintFormula {
  private final PsiLambdaExpression myExpression;
  private PsiType myT;

  public LambdaExpressionCompatibilityConstraint(PsiLambdaExpression expression, PsiType t) {
    myExpression = expression;
    myT = t;
  }

  @Override
  public boolean reduce(InferenceSession session, List<ConstraintFormula> constraints) {
    if (LambdaHighlightingUtil.checkInterfaceFunctional(myT) != null) {
      return false;
    }

    if (myExpression.hasFormalParameterTypes()) {
    }
    final PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(
      FunctionalInterfaceParameterizationUtil.getFunctionalType(myT, myExpression, false));
    final PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(resolveResult);
    if (interfaceMethod == null) {
      return false;
    }
    final PsiSubstitutor substitutor = LambdaUtil.getSubstitutor(interfaceMethod, resolveResult);
    final PsiParameter[] parameters = interfaceMethod.getParameterList().getParameters();

    final PsiParameter[] lambdaParameters = myExpression.getParameterList().getParameters();
    if (lambdaParameters.length != parameters.length) {
      return false;
    }
    if (myExpression.hasFormalParameterTypes()) {
      for (int i = 0; i < lambdaParameters.length; i++) {
        constraints.add(new TypeEqualityConstraint(lambdaParameters[i].getType(), substitutor.substitute(parameters[i].getType())));
      }
    } else {
      for (PsiParameter parameter : parameters) {
        if (!session.isProperType(substitutor.substitute(parameter.getType()))) {
          return false;
        }
      }
    }

    final PsiType returnType = interfaceMethod.getReturnType();
    if (returnType != null) {
      final List<PsiExpression> returnExpressions = LambdaUtil.getReturnExpressions(myExpression);
      if (returnType.equals(PsiType.VOID)) {
        if (!returnExpressions.isEmpty() && !(myExpression.getBody() instanceof PsiExpression)) {
          return false;
        }
      } else {
        if (returnExpressions.isEmpty()) {  //not value-compatible
          return false;
        }
        for (PsiExpression returnExpression : returnExpressions) {
          constraints.add(new ExpressionCompatibilityConstraint(returnExpression, substitutor.substitute(returnType)));
        }
      }
    }
    return true;
  }

  @Override
  public void apply(PsiSubstitutor substitutor) {
    myT = substitutor.substitute(myT);
  }
}
