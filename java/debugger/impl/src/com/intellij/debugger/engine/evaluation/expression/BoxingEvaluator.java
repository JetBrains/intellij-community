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
package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.JVMNameUtil;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.psi.CommonClassNames;
import com.sun.jdi.*;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: Feb 8, 2010
 */
public class BoxingEvaluator implements Evaluator{
  private final Evaluator myOperand;

  public BoxingEvaluator(Evaluator operand) {
    myOperand = new DisableGC(operand);
  }

  public Object evaluate(EvaluationContextImpl context) throws EvaluateException {
    final Object result = myOperand.evaluate(context);
    if (result == null || result instanceof ObjectReference) {
      return result;
    }

    if (result instanceof BooleanValue) {
      return convertToWrapper(context, (BooleanValue)result, CommonClassNames.JAVA_LANG_BOOLEAN);
    }
    if (result instanceof ByteValue) {
      return convertToWrapper(context, (ByteValue)result, CommonClassNames.JAVA_LANG_BYTE);
    }
    if (result instanceof CharValue) {
      return convertToWrapper(context, (CharValue)result, CommonClassNames.JAVA_LANG_CHARACTER);
    }
    if (result instanceof ShortValue) {
      return convertToWrapper(context, (ShortValue)result, CommonClassNames.JAVA_LANG_SHORT);
    }
    if (result instanceof IntegerValue) {
      return convertToWrapper(context, (IntegerValue)result, CommonClassNames.JAVA_LANG_INTEGER);
    }
    if (result instanceof LongValue) {
      return convertToWrapper(context, (LongValue)result, CommonClassNames.JAVA_LANG_LONG);
    }
    if (result instanceof FloatValue) {
      return convertToWrapper(context, (FloatValue)result, CommonClassNames.JAVA_LANG_FLOAT);
    }
    if (result instanceof DoubleValue) {
      return convertToWrapper(context, (DoubleValue)result, CommonClassNames.JAVA_LANG_DOUBLE);
    }
    throw new EvaluateException("Cannot perform boxing conversion for a value of type " + ((Value)result).type().name());
  }

  @Nullable
  public Modifier getModifier() {
    return null;
  }

  private static Value convertToWrapper(EvaluationContextImpl context, PrimitiveValue value, String wrapperTypeName) throws
                                                                                                                            EvaluateException {
    final DebugProcessImpl process = context.getDebugProcess();
    final ClassType wrapperClass = (ClassType)process.findClass(context, wrapperTypeName, null);
    final String methodSignature = "(" + JVMNameUtil.getPrimitiveSignature(value.type().name()) + ")L" + wrapperTypeName.replace('.', '/') + ";";

    List<Method> methods = wrapperClass.methodsByName("valueOf", methodSignature);
    if (methods.size() == 0) { // older JDK version
      methods = wrapperClass.methodsByName(JVMNameUtil.CONSTRUCTOR_NAME, methodSignature);
    }
    if (methods.size() == 0) {
      throw new EvaluateException("Cannot construct wrapper object for value of type " + value.type() + ": Unable to find either valueOf() or constructor method");
    }

    return process.invokeMethod(context, wrapperClass, methods.get(0), Collections.singletonList(value));
  }
}
