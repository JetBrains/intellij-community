/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.JVMName;
import com.intellij.debugger.engine.JVMNameUtil;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ArrayUtil;
import com.sun.jdi.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class NewClassInstanceEvaluator implements Evaluator {
  private static final Logger LOG = Logger.getInstance(NewClassInstanceEvaluator.class);

  private final TypeEvaluator myClassTypeEvaluator;
  private final JVMName myConstructorSignature;
  private final Evaluator[] myParamsEvaluators;

  public NewClassInstanceEvaluator(TypeEvaluator classTypeEvaluator, JVMName constructorSignature, Evaluator[] argumentEvaluators) {
    myClassTypeEvaluator = classTypeEvaluator;
    myConstructorSignature = constructorSignature;
    myParamsEvaluators = argumentEvaluators;
  }

  public Object evaluate(EvaluationContextImpl context) throws EvaluateException {
    DebugProcessImpl debugProcess = context.getDebugProcess();
    Object obj = myClassTypeEvaluator.evaluate(context);
    if (!(obj instanceof ClassType)) {
      throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.cannot.evaluate.class.type"));
    }
    ClassType classType = (ClassType)obj;
    // find constructor
    Method method = DebuggerUtils.findMethod(classType, JVMNameUtil.CONSTRUCTOR_NAME, myConstructorSignature.getName(debugProcess));
    if (method == null) {
      throw EvaluateExceptionUtil.createEvaluateException(
        DebuggerBundle.message("evaluation.error.cannot.resolve.constructor", myConstructorSignature.getDisplayName(debugProcess)));
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
