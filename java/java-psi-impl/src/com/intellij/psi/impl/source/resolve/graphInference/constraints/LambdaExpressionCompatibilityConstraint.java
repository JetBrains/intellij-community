package com.intellij.psi.impl.source.resolve.graphInference.constraints;

import com.intellij.psi.LambdaHighlightingUtil;
import com.intellij.psi.PsiLambdaExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.source.resolve.graphInference.InferenceSession;
import com.intellij.psi.impl.source.resolve.graphInference.InferenceVariable;

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
    
    return true;
  }
}
