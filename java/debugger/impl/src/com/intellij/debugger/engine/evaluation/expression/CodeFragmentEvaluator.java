// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.debugger.engine.evaluation.EvaluateRuntimeException;
import com.intellij.debugger.jdi.VirtualMachineProxyImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.sun.jdi.Value;

import java.util.HashMap;
import java.util.Map;

/**
 * @author lex
 */
public class CodeFragmentEvaluator extends BlockStatementEvaluator{
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.engine.evaluation.expression.CodeFragmentEvaluator");

  private final CodeFragmentEvaluator myParentFragmentEvaluator;
  private final Map<String, Object> mySyntheticLocals = new HashMap<>();

  public CodeFragmentEvaluator(CodeFragmentEvaluator parentFragmentEvaluator) {
    super(null);
    myParentFragmentEvaluator = parentFragmentEvaluator;
  }

  public void setStatements(Evaluator[] evaluators) {
    myStatements = evaluators;
  }

  public Value getValue(String localName, VirtualMachineProxyImpl vm) throws EvaluateException {
    if(!mySyntheticLocals.containsKey(localName)) {
      if(myParentFragmentEvaluator != null){
        return myParentFragmentEvaluator.getValue(localName, vm);
      } else {
        throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.variable.not.declared", localName));
      }
    }
    Object value = mySyntheticLocals.get(localName);
    if(value instanceof Value) {
      return (Value)value;
    }
    else if(value == null) {
      return null;
    }
    else if(value instanceof Boolean) {
      return vm.mirrorOf(((Boolean)value).booleanValue());
    }
    else if(value instanceof Byte) {
      return vm.mirrorOf(((Byte)value).byteValue());
    }
    else if(value instanceof Character) {
      return vm.mirrorOf(((Character)value).charValue());
    }
    else if(value instanceof Short) {
      return vm.mirrorOf(((Short)value).shortValue());
    }
    else if(value instanceof Integer) {
      return vm.mirrorOf(((Integer)value).intValue());
    }
    else if(value instanceof Long) {
      return vm.mirrorOf(((Long)value).longValue());
    }
    else if(value instanceof Float) {
      return vm.mirrorOf(((Float)value).floatValue());
    }
    else if(value instanceof Double) {
      return vm.mirrorOf(((Double)value).doubleValue());
    }
    else if(value instanceof String) {
      return vm.mirrorOf((String)value);
    }
    else {
      LOG.error("unknown default initializer type " + value.getClass().getName());
      return null;
    }
  }

  private boolean hasValue(String localName) {
    if(!mySyntheticLocals.containsKey(localName)) {
      if(myParentFragmentEvaluator != null){
        return myParentFragmentEvaluator.hasValue(localName);
      } else {
        return false;
      }
    } else {
      return true;
    }
  }

  public void setInitialValue(String localName, Object value) {
    LOG.assertTrue(!(value instanceof Value), "use setValue for jdi values");
    if(hasValue(localName)) {
      throw new EvaluateRuntimeException(
        EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.variable.already.declared", localName)));
    }
    mySyntheticLocals.put(localName, value);
  }

  public void setValue(String localName, Value value) throws EvaluateException {
    if(!mySyntheticLocals.containsKey(localName)) {
      if(myParentFragmentEvaluator != null){
        myParentFragmentEvaluator.setValue(localName, value);
      } else {
        throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.variable.not.declared", localName));
      }
    }
    else {
      mySyntheticLocals.put(localName, value);
    }
  }
}
