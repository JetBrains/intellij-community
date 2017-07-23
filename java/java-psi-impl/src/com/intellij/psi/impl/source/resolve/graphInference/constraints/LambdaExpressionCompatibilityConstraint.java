package com.intellij.psi.impl.source.resolve.graphInference.constraints;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.graphInference.FunctionalInterfaceParameterizationUtil;
import com.intellij.psi.impl.source.resolve.graphInference.InferenceSession;
import com.intellij.psi.impl.source.resolve.graphInference.InferenceVariable;
import com.intellij.psi.impl.source.resolve.graphInference.PsiPolyExpressionUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;

import java.util.List;

public class LambdaExpressionCompatibilityConstraint implements ConstraintFormula {
  private static final Logger LOG = Logger.getInstance(LambdaExpressionCompatibilityConstraint.class);
  private final PsiLambdaExpression myExpression;
  private PsiType myT;

  public LambdaExpressionCompatibilityConstraint(PsiLambdaExpression expression, PsiType t) {
    myExpression = expression;
    myT = t;
  }

  @Override
  public boolean reduce(InferenceSession session, List<ConstraintFormula> constraints) {
    if (!LambdaUtil.isFunctionalType(myT)) {
      session.registerIncompatibleErrorMessage(session.getPresentableText(myT) + " is not a functional interface");
      return false;
    }

    final PsiType groundTargetType = FunctionalInterfaceParameterizationUtil.getGroundTargetType(myT, myExpression, false);
    final PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(groundTargetType);
    final PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(resolveResult);
    if (interfaceMethod == null) {
      session.registerIncompatibleErrorMessage("No valid function type can be found for " + session.getPresentableText(myT));
      return false;
    }
    final PsiSubstitutor substitutor = LambdaUtil.getSubstitutor(interfaceMethod, resolveResult);
    final PsiParameter[] parameters = interfaceMethod.getParameterList().getParameters();

    final PsiParameter[] lambdaParameters = myExpression.getParameterList().getParameters();
    if (lambdaParameters.length != parameters.length) {
      session.registerIncompatibleErrorMessage("Incompatible parameter types in lambda expression");
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
          session.registerIncompatibleErrorMessage("Incompatible types: expected void but the lambda body is neither a statement expression nor a void-compatible block");
          return false;
        }
      }
      else {
        if (lambdaBody instanceof PsiCodeBlock && !myExpression.isValueCompatible()) {
          session.registerIncompatibleErrorMessage("Incompatible types: expected not void but the lambda body is a block that is not value-compatible");
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
            constraints.add(new ExpressionCompatibilityConstraint(returnExpression, returnType));
          }
        }
        else {
          for (PsiExpression returnExpression : returnExpressions) {
            if (!PsiPolyExpressionUtil.isPolyExpression(returnExpression)) {
              if (!TypeConversionUtil.areTypesAssignmentCompatible(returnType, returnExpression)) {
                final PsiType type = returnExpression.getType();
                if (type != null) {
                  session.registerIncompatibleErrorMessage("Bad return type in lambda expression: " + session.getPresentableText(type) + " cannot be converted to " + session.getPresentableText(returnType));
                }
                else {
                  session.registerIncompatibleErrorMessage(returnExpression.getText() + " is not compatible with " + session.getPresentableText(returnType));
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
