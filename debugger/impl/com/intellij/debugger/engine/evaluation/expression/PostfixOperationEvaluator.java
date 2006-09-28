package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.psi.TokenTypeEx;
import com.intellij.psi.tree.IElementType;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NonNls;

/**
 * Created by IntelliJ IDEA.
 * User: lex
 * Date: Apr 20, 2004
 * Time: 5:34:50 PM
 * To change this template use File | Settings | File Templates.
 */
public class PostfixOperationEvaluator implements Evaluator{
  private final Evaluator myOperandEvaluator;
  private static final @NonNls Evaluator myRightEvaluator = new LiteralEvaluator(new Integer(1), "byte");

  private IElementType myOpType;
  private String myExpectedType; // a result of PsiType.getCanonicalText()

  private Modifier myModifier;

  public PostfixOperationEvaluator(Evaluator operandEvaluator, IElementType opType, String expectedType) {
    myOperandEvaluator = operandEvaluator;
    myOpType = opType;
    myExpectedType = expectedType;
  }

  public Object evaluate(EvaluationContextImpl context) throws EvaluateException {
    final Object value = myOperandEvaluator.evaluate(context);
    myModifier = myOperandEvaluator.getModifier();
    IElementType opType = myOpType == TokenTypeEx.PLUSPLUS ? TokenTypeEx.PLUS : TokenTypeEx.MINUS;
    Object operationResult = BinaryExpressionEvaluator.evaluateOperation((Value)value, opType, myRightEvaluator, myExpectedType, context);
    AssignmentEvaluator.assign(myModifier, operationResult, context);
    return value;
  }

  public Modifier getModifier() {
    return myModifier;
  }
}
