// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.openapi.diagnostic.Logger;
import com.sun.jdi.*;

import java.util.ArrayList;
import java.util.Arrays;

class NewArrayInstanceEvaluator implements Evaluator {
  private static final Logger LOG = Logger.getInstance(NewArrayInstanceEvaluator.class);
  private final Evaluator myArrayTypeEvaluator;
  private final Evaluator myDimensionEvaluator;
  private final Evaluator myInitializerEvaluator;

  /**
   * either dimensionEvaluator or initializerEvaluators must be null!
   */
  NewArrayInstanceEvaluator(Evaluator arrayTypeEvaluator, Evaluator dimensionEvaluator, Evaluator initializerEvaluator) {
    myArrayTypeEvaluator = arrayTypeEvaluator;
    myDimensionEvaluator = dimensionEvaluator;
    myInitializerEvaluator = initializerEvaluator;
  }

  @Override
  public Object evaluate(EvaluationContextImpl context) throws EvaluateException {
//    throw new EvaluateException("Creating new array instances is not supported yet", true);
    DebugProcessImpl debugProcess = context.getDebugProcess();
    Object obj = myArrayTypeEvaluator.evaluate(context);
    if (!(obj instanceof ArrayType arrayType)) {
      throw EvaluateExceptionUtil.createEvaluateException(JavaDebuggerBundle.message("evaluation.error.array.type.expected"));
    }
    int dimension;
    Object[] initialValues = null;
    if (myDimensionEvaluator != null) {
      Object o = myDimensionEvaluator.evaluate(context);
      if (!(o instanceof Value && DebuggerUtils.isNumeric((Value)o))) {
        throw EvaluateExceptionUtil.createEvaluateException(
          JavaDebuggerBundle.message("evaluation.error.array.dimention.numeric.value.expected")
        );
      }
      PrimitiveValue value = (PrimitiveValue)o;
      dimension = value.intValue();
    }
    else { // myInitializerEvaluator must not be null
      Object o = myInitializerEvaluator.evaluate(context);
      if (!(o instanceof Object[])) {
        throw EvaluateExceptionUtil.createEvaluateException(JavaDebuggerBundle.message("evaluation.error.cannot.evaluate.array.initializer"));
      }
      initialValues = (Object[])o;
      dimension = initialValues.length;
    }
    ArrayReference arrayReference = DebuggerUtilsEx.mirrorOfArray(arrayType, dimension, context);
    if (initialValues != null && initialValues.length > 0) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("Setting initial values: dimension = " + dimension + "; array size is " + initialValues.length);
      }
      setInitialValues(arrayReference, initialValues, context);
    }
    return arrayReference;
  }

  private static void setInitialValues(ArrayReference arrayReference, Object[] values, EvaluationContextImpl context) throws EvaluateException {
    ArrayType type = (ArrayType)arrayReference.referenceType();
    DebugProcessImpl debugProcess = context.getDebugProcess();
    try {
      if (type.componentType() instanceof ArrayType componentType) {
        int length = arrayReference.length();
        for (int idx = 0; idx < length; idx++) {
          Object value = values[idx];
          if (value instanceof Value) {
            arrayReference.setValue(idx, (Value)value);
          }
          else {
            ArrayReference componentArray = (ArrayReference)arrayReference.getValue(idx);
            Object[] componentArrayValues = (Object[])value;
            if (componentArray == null) {
              componentArray = DebuggerUtilsEx.mirrorOfArray(componentType, componentArrayValues.length, context);
              arrayReference.setValue(idx, componentArray);
            }
            setInitialValues(componentArray, componentArrayValues, context);
          }
        }
      }
      else {
        if (values.length > 0) {
          arrayReference.setValues(new ArrayList(Arrays.asList(values)));
        }
      }
    }
    catch (ClassNotLoadedException ex) {
      final ReferenceType referenceType;
      try {
        referenceType = context.isAutoLoadClasses() ? debugProcess.loadClass(context, ex.className(), type.classLoader()) : null;
      }
      catch (InvocationException | InvalidTypeException | IncompatibleThreadStateException | ClassNotLoadedException e) {
        throw EvaluateExceptionUtil.createEvaluateException(e);
      }
      if (referenceType != null) {
        setInitialValues(arrayReference, values, context);
      }
      else {
        throw EvaluateExceptionUtil.createEvaluateException(JavaDebuggerBundle.message("error.class.not.loaded", ex.className()));
      }
    }
    catch (InvalidTypeException ex) {
      throw EvaluateExceptionUtil.createEvaluateException(JavaDebuggerBundle.message("evaluation.error.incompatible.array.initializer.type"));
    }
    catch (IndexOutOfBoundsException ex) {
      throw EvaluateExceptionUtil.createEvaluateException(JavaDebuggerBundle.message("evaluation.error.invalid.array.size"));
    }
    catch (ClassCastException ex) {
      throw EvaluateExceptionUtil.createEvaluateException(JavaDebuggerBundle.message("evaluation.error.cannot.initialize.array"));
    }
  }
}
