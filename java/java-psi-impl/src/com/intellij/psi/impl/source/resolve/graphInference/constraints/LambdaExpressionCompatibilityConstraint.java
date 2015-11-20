package com.intellij.psi.impl.source.resolve.graphInference.constraints;

import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.graphInference.FunctionalInterfaceParameterizationUtil;
import com.intellij.psi.impl.source.resolve.graphInference.InferenceSession;
import com.intellij.psi.impl.source.resolve.graphInference.PsiPolyExpressionUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;

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
      session.registerIncompatibleErrorMessage(myT.getPresentableText() + " is not a functional interface");
      return false;
    }

    final PsiType groundTargetType = FunctionalInterfaceParameterizationUtil.getGroundTargetType(myT, myExpression, false);
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
      final PsiElement lambdaBody = myExpression.getBody();
      if (returnType.equals(PsiType.VOID)) {
        if (!(lambdaBody instanceof PsiCodeBlock && myExpression.isVoidCompatible()) && !LambdaUtil.isExpressionStatementExpression(lambdaBody)) {
          return false;
        }
      }
      else {
        if (lambdaBody instanceof PsiCodeBlock && !myExpression.isValueCompatible()) {
          return false;
        }
        InferenceSession callsession = session.getInferenceSessionContainer().findNestedCallSession(myExpression, session);
        returnType = callsession.substituteWithInferenceVariables(substitutor.substitute(returnType));
        if (!callsession.isProperType(returnType)) {
          for (PsiExpression returnExpression : returnExpressions) {
            constraints.add(new ExpressionCompatibilityConstraint(returnExpression, returnType));
          }
        }
        else {
          for (PsiExpression returnExpression : returnExpressions) {
            if (!PsiPolyExpressionUtil.isPolyExpression(returnExpression)) {
              if (!TypeConversionUtil.areTypesAssignmentCompatible(returnType, returnExpression)) {
                final PsiType type = returnExpression.getType();
                if (type != null) {
                  session.registerIncompatibleErrorMessage("Bad return type in lambda expression: " + type.getPresentableText() + " cannot be converted to " + returnType.getPresentableText());
                }
                else {
                  session.registerIncompatibleErrorMessage(returnExpression.getText() + " is not compatible with " + returnType.getPresentableText());
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
