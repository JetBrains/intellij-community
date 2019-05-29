// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.openapi.util.text.StringUtil;
import com.sun.jdi.Value;

import java.util.ArrayList;
import java.util.List;

/**
 * @author egor
 */
public class ExpressionListEvaluator implements Evaluator {
  private final List<? extends Evaluator> myEvaluators;

  public ExpressionListEvaluator(List<? extends Evaluator> evaluators) {
    myEvaluators = evaluators;
  }

  @Override
  public Object evaluate(EvaluationContextImpl context) throws EvaluateException {
    List<String> strings = new ArrayList<>(myEvaluators.size());
    for (Evaluator evaluator : myEvaluators) {
      strings.add(DebuggerUtils.getValueAsString(context, (Value)evaluator.evaluate(context)));
    }
    return DebuggerUtilsEx.mirrorOfString(StringUtil.join(strings, ", "),
                                          context.getDebugProcess().getVirtualMachineProxy(), context);
  }
}
