/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

/**
 * class NewArrayInstanceEvaluator
 * created Jun 27, 2001
 * @author Jeka
 */
package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.sun.jdi.*;

import java.util.ArrayList;
import java.util.Arrays;

class NewArrayInstanceEvaluator implements Evaluator {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.engine.evaluation.expression.NewArrayInstanceEvaluator");
  private final Evaluator myArrayTypeEvaluator;
  private Evaluator myDimensionEvaluator = null;
  private Evaluator myInitializerEvaluator = null;

  /**
   * either dimensionEvaluator or initializerEvaluators must be null!
   */
  public NewArrayInstanceEvaluator(Evaluator arrayTypeEvaluator, Evaluator dimensionEvaluator, Evaluator initializerEvaluator) {
    myArrayTypeEvaluator = arrayTypeEvaluator;
    myDimensionEvaluator = dimensionEvaluator;
    myInitializerEvaluator = initializerEvaluator;
  }

  public Object evaluate(EvaluationContextImpl context) throws EvaluateException {
//    throw new EvaluateException("Creating new array instances is not supported yet", true);
    DebugProcessImpl debugProcess = context.getDebugProcess();
    Object obj = myArrayTypeEvaluator.evaluate(context);
    if (!(obj instanceof ArrayType)) {
      throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.array.type.expected"));
    }
    ArrayType arrayType = (ArrayType)obj;
    int dimension;
    Object[] initialValues = null;
    if (myDimensionEvaluator != null) {
      Object o = myDimensionEvaluator.evaluate(context);
      if (!(o instanceof Value && DebuggerUtils.isNumeric((Value)o))) {
        throw EvaluateExceptionUtil.createEvaluateException(
          DebuggerBundle.message("evaluation.error.array.dimention.numeric.value.expected")
        );
      }
      PrimitiveValue value = (PrimitiveValue)o;
      dimension = value.intValue();
    }
    else { // myInitializerEvaluator must not be null
      Object o = myInitializerEvaluator.evaluate(context);
      if (!(o instanceof Object[])) {
        throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.cannot.evaluate.array.initializer"));
      }
      initialValues = (Object[])o;
      dimension = initialValues.length;
    }
    ArrayReference arrayReference = debugProcess.newInstance(arrayType, dimension);
    if (initialValues != null && initialValues.length > 0) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("Setting initial values: dimension = "+dimension + "; array size is "+initialValues.length);
      }
      setInitialValues(arrayReference, initialValues, context);
    }
    return arrayReference;
  }

  private static void setInitialValues(ArrayReference arrayReference, Object[] values, EvaluationContextImpl context) throws EvaluateException {
    ArrayType type = (ArrayType)arrayReference.referenceType();
    DebugProcessImpl debugProcess = context.getDebugProcess();
    try {
      if (type.componentType() instanceof ArrayType) {
        ArrayType componentType = (ArrayType)type.componentType();
        int length = arrayReference.length();
        for (int idx = 0; idx < length; idx++) {
          ArrayReference componentArray = (ArrayReference)arrayReference.getValue(idx);
          Object value = values[idx];
          if (value instanceof Value) {
            arrayReference.setValue(idx, (Value)value);
          }
          else {
            Object[] componentArrayValues = (Object[])value;
            if (componentArray == null) {
              componentArray = debugProcess.newInstance(componentType, componentArrayValues.length);
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
        referenceType = context.isAutoLoadClasses()? debugProcess.loadClass(context, ex.className(), type.classLoader()) : null;
      }
      catch (InvocationException e) {
        throw EvaluateExceptionUtil.createEvaluateException(e);
      }
      catch (ClassNotLoadedException e) {
        throw EvaluateExceptionUtil.createEvaluateException(e);
      }
      catch (IncompatibleThreadStateException e) {
        throw EvaluateExceptionUtil.createEvaluateException(e);
      }
      catch (InvalidTypeException e) {
        throw EvaluateExceptionUtil.createEvaluateException(e);
      }
      if (referenceType != null) {
        setInitialValues(arrayReference, values, context);
      }
      else {
        throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("error.class.not.loaded", ex.className()));
      }
    }
    catch (InvalidTypeException ex) {
      throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.incompatible.array.initializer.type"));
    }
    catch (IndexOutOfBoundsException ex) {
      throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.invalid.array.size"));
    }
    catch (ClassCastException ex) {
      throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.cannot.initialize.array"));
    }
  }

  public Modifier getModifier() {
    return null;
  }
}
