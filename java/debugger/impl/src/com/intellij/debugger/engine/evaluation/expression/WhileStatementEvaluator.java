package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.openapi.util.Comparing;
import com.sun.jdi.BooleanValue;

/**
 * Created by IntelliJ IDEA.
 * User: lex
 * Date: Apr 20, 2004
 * Time: 5:03:47 PM
 * To change this template use File | Settings | File Templates.
 */
public class WhileStatementEvaluator implements Evaluator {
  private final Evaluator myConditionEvaluator;
  private final Evaluator myBodyEvaluator;
  private final String myLabelName;

  public WhileStatementEvaluator(Evaluator conditionEvaluator, Evaluator bodyEvaluator, String labelName) {
    myConditionEvaluator = conditionEvaluator;
    myBodyEvaluator = bodyEvaluator;
    myLabelName = labelName;
  }

  public Modifier getModifier() {
    return myConditionEvaluator.getModifier();
  }

  public Object evaluate(EvaluationContextImpl context) throws EvaluateException {
    Object value;
    for (;;) {
      value = myConditionEvaluator.evaluate(context);
      if(!(value instanceof BooleanValue)) {
        throw EvaluateExceptionUtil.BOOLEAN_EXPECTED;
      } else {
        if(!((BooleanValue)value).booleanValue()) {
          break;
        }
      }
      try {
        myBodyEvaluator.evaluate(context);
      }
      catch (BreakException e) {
        if(Comparing.equal(e.getLabelName(), myLabelName)) {
          break;
        } else
          throw e;
      }
      catch (ContinueException e) {
        if(Comparing.equal(e.getLabelName(), myLabelName)) {
          continue;
        } else
          throw e;
      }
    }

    return value;
  }

}
