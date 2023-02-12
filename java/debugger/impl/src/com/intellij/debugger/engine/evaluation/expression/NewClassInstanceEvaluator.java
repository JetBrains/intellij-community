// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.JVMName;
import com.intellij.debugger.engine.JVMNameUtil;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ArrayUtil;
import com.sun.jdi.ClassType;
import com.sun.jdi.Method;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.Value;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class NewClassInstanceEvaluator implements Evaluator {
  private static final Logger LOG = Logger.getInstance(NewClassInstanceEvaluator.class);

  private final TypeEvaluator myClassTypeEvaluator;
  private final JVMName myConstructorSignature;
  private final Evaluator[] myParamsEvaluators;

  NewClassInstanceEvaluator(TypeEvaluator classTypeEvaluator, JVMName constructorSignature, Evaluator[] argumentEvaluators) {
    myClassTypeEvaluator = classTypeEvaluator;
    myConstructorSignature = constructorSignature;
    myParamsEvaluators = argumentEvaluators;
  }

  @Override
  public Object evaluate(EvaluationContextImpl context) throws EvaluateException {
    DebugProcessImpl debugProcess = context.getDebugProcess();
    Object obj = myClassTypeEvaluator.evaluate(context);
    if (!(obj instanceof ClassType classType)) {
      throw EvaluateExceptionUtil.createEvaluateException(JavaDebuggerBundle.message("evaluation.error.cannot.evaluate.class.type"));
    }
    // find constructor
    Method method = DebuggerUtils.findMethod(classType, JVMNameUtil.CONSTRUCTOR_NAME, myConstructorSignature.getName(debugProcess));
    if (method == null) {
      throw EvaluateExceptionUtil.createEvaluateException(
        JavaDebuggerBundle.message("evaluation.error.cannot.resolve.constructor", myConstructorSignature.getDisplayName(debugProcess)));
    }
    // evaluate arguments
    List<Value> arguments;
    if (!ArrayUtil.isEmpty(myParamsEvaluators)) {
      arguments = new ArrayList<>(myParamsEvaluators.length);
      for (Evaluator evaluator : myParamsEvaluators) {
        Object res = evaluator.evaluate(context);
        if (!(res instanceof Value) && res != null) {
          LOG.error("Unable to call newInstance, evaluator " + evaluator + " result is not Value, but " + res);
        }
        //noinspection ConstantConditions
        arguments.add((Value)res);
      }
    }
    else {
      arguments = Collections.emptyList();
    }
    ObjectReference objRef;
    try {
      objRef = debugProcess.newInstance(context, classType, method, arguments);
    }
    catch (EvaluateException e) {
      throw EvaluateExceptionUtil.createEvaluateException(e);
    }
    return objRef;
  }
}
