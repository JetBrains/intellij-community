package com.intellij.psi.impl.source.resolve.graphInference.constraints;

import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.graphInference.InferenceSession;
import com.intellij.psi.impl.source.resolve.graphInference.InferenceVariable;
import com.intellij.psi.util.PsiUtil;

import java.util.List;

/**
 * User: anna
 */
public class LambdaExpressionCompatibilityConstraint implements ConstraintFormula {
  private final PsiLambdaExpression myExpression;
  private final PsiType myT;

  public LambdaExpressionCompatibilityConstraint(PsiLambdaExpression expression, PsiType t) {
    myExpression = expression;
    myT = t;
  }

  @Override
  public boolean reduce(InferenceSession session, List<ConstraintFormula> constraints, List<ConstraintFormula> delayedConstraints) {
    final InferenceVariable inferenceVariable = session.getInferenceVariable(myT);
    if (inferenceVariable != null) {
      delayedConstraints.add(this);
      return true;
    }
    if (LambdaHighlightingUtil.checkInterfaceFunctional(myT) != null) {
      return false;
    }

    if (myExpression.hasFormalParameterTypes()) {
    }
    final PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(myT);
    if (interfaceMethod == null) {
      return false;
    }
    final PsiSubstitutor substitutor = LambdaUtil.getSubstitutor(interfaceMethod, PsiUtil.resolveGenericsClassInType(myT));
    final PsiParameter[] parameters = interfaceMethod.getParameterList().getParameters();
    for (PsiParameter parameter : parameters) {
      if (!session.isProperType(substitutor.substitute(parameter.getType()))) {
        delayedConstraints.add(this);
        return true;
      }
    }

    final PsiParameter[] lambdaParameters = myExpression.getParameterList().getParameters();
    if (lambdaParameters.length != parameters.length) {
      return false;
    }
    if (myExpression.hasFormalParameterTypes()) {
      for (int i = 0; i < lambdaParameters.length; i++) {
        constraints.add(new TypeEqualityConstraint(lambdaParameters[i].getType(), substitutor.substitute(parameters[i].getType())));
      }
    }

    final PsiType returnType = interfaceMethod.getReturnType();
    if (returnType != null) {
      if (returnType.equals(PsiType.VOID)) {
        if (!myExpression.isVoidCompatible() && !(myExpression.getBody() instanceof PsiExpression)) {
          return false;
        }
      } else {
        if (myExpression.isVoidCompatible()) {  //not value-compatible
          return false;
        }
        for (PsiExpression returnExpressions : LambdaUtil.getReturnExpressions(myExpression)) {
          constraints.add(new ExpressionCompatibilityConstraint(returnExpressions, substitutor.substitute(returnType)));
        }
      }
    }
    return true;
  }
}
