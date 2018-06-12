// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * Class InstanceofEvaluator
 * @author Jeka
 */
package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.sun.jdi.*;

import java.util.Collections;

class InstanceofEvaluator implements Evaluator {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.engine.evaluation.expression.InstanceofEvaluator");
  private final Evaluator myOperandEvaluator;
  private final TypeEvaluator myTypeEvaluator;

  public InstanceofEvaluator(Evaluator operandEvaluator, TypeEvaluator typeEvaluator) {
    myOperandEvaluator = operandEvaluator;
    myTypeEvaluator = typeEvaluator;
  }

  public Object evaluate(EvaluationContextImpl context) throws EvaluateException {
    Value value = (Value)myOperandEvaluator.evaluate(context);
    if (value == null) {
      return context.getDebugProcess().getVirtualMachineProxy().mirrorOf(false);
    }
    if (!(value instanceof ObjectReference)) {
      throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.object.reference.expected"));
    }
    try {
      ReferenceType refType = (ReferenceType)myTypeEvaluator.evaluate(context);
      ClassObjectReference classObject = refType.classObject();
      ClassType classRefType = (ClassType)classObject.referenceType();
      //noinspection HardCodedStringLiteral
      Method method = classRefType.concreteMethodByName("isAssignableFrom", "(Ljava/lang/Class;)Z");
      return context.getDebugProcess().invokeMethod(context, classObject, method,
                                                    Collections.singletonList(((ObjectReference)value).referenceType().classObject()));
    }
    catch (Exception e) {
      LOG.debug(e);
      throw EvaluateExceptionUtil.createEvaluateException(e);
    }
  }

  @Override
  public String toString() {
    return myOperandEvaluator + " instanceof " + myTypeEvaluator;
  }
}
