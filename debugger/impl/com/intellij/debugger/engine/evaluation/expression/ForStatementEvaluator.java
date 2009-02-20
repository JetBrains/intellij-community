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
public class ForStatementEvaluator implements Evaluator {
  private final Evaluator myInitializationEvaluator;
  private final Evaluator myConditionEvaluator;
  private final Evaluator myUpdateEvaluator;
  private final Evaluator myBodyEvaluator;

  private Modifier myModifier;
  private final String   myLabelName;

  public ForStatementEvaluator(Evaluator initializationEvaluator, Evaluator conditionEvaluator, Evaluator updateEvaluator, Evaluator bodyEvaluator, String labelName) {
    myInitializationEvaluator = initializationEvaluator;
    myConditionEvaluator = conditionEvaluator;
    myUpdateEvaluator = updateEvaluator;
    myBodyEvaluator = bodyEvaluator;
    myLabelName = labelName;
  }

  public Modifier getModifier() {
    return myModifier;
  }

  public Object evaluate(EvaluationContextImpl context) throws EvaluateException {
    Object value = context.getDebugProcess().getVirtualMachineProxy().mirrorOf();
    if(myInitializationEvaluator != null) {
      value = myInitializationEvaluator.evaluate(context);
      myModifier = myInitializationEvaluator.getModifier();
    }

    for (;;) {
      if(myConditionEvaluator != null) {
        value = myConditionEvaluator.evaluate(context);
        myModifier = myConditionEvaluator.getModifier();
        if(!(value instanceof BooleanValue)) {
          throw EvaluateExceptionUtil.BOOLEAN_EXPECTED;
        } else {
          if(!((BooleanValue)value).booleanValue()) {
            break;
          }
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
          //continue;
        } else
          throw e;
      }

      if(myUpdateEvaluator != null) {
        value = myUpdateEvaluator.evaluate(context);
        myModifier = myUpdateEvaluator.getModifier();
      }
    }

    return value;
  }

}
