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
    if (!LambdaUtil.isFunctionalType(myT)) {
      return false;
    }

    final PsiType groundTargetType = FunctionalInterfaceParameterizationUtil.getGroundTargetType(myT, myExpression);
    final PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(groundTargetType);
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
      constraints.add(new StrictSubtypingConstraint(myT, groundTargetType));
    } else {
      for (PsiParameter parameter : parameters) {
        if (!session.isProperType(session.substituteWithInferenceVariables(substitutor.substitute(parameter.getType())))) {
          return false;
        }
      }
    }

    PsiType returnType = interfaceMethod.getReturnType();
    if (returnType != null) {
      final List<PsiExpression> returnExpressions = LambdaUtil.getReturnExpressions(myExpression);
      if (returnType.equals(PsiType.VOID)) {
        if (!returnExpressions.isEmpty() && !(myExpression.getBody() instanceof PsiExpression)) {
          return false;
        }
      } else {
        if (returnExpressions.isEmpty() && !myExpression.isValueCompatible()) {  //not value-compatible
          return false;
        }
        returnType = session.substituteWithInferenceVariables(substitutor.substitute(returnType));
        if (!session.isProperType(returnType)) {
          for (PsiExpression returnExpression : returnExpressions) {
            constraints.add(new ExpressionCompatibilityConstraint(returnExpression, returnType));
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
