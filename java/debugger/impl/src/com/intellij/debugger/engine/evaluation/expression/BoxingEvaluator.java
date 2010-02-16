/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
import com.sun.jdi.*;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: Feb 8, 2010
 */
public class BoxingEvaluator implements Evaluator{
  private final Evaluator myOperand;

  public BoxingEvaluator(Evaluator operand) {
    myOperand = operand;
  }

  public Object evaluate(EvaluationContextImpl context) throws EvaluateException {
    final Object result = myOperand.evaluate(context);
    if (result == null || result instanceof ObjectReference) {
      return result;
    }

    if (result instanceof BooleanValue) {
      return convertToWrapper(context, (BooleanValue)result, "java.lang.Boolean");
    }
    if (result instanceof ByteValue) {
      return convertToWrapper(context, (ByteValue)result, "java.lang.Byte");
    }
    if (result instanceof CharValue) {
      return convertToWrapper(context, (CharValue)result, "java.lang.Character");
    }
    if (result instanceof ShortValue) {
      return convertToWrapper(context, (ShortValue)result, "java.lang.Short");
    }
    if (result instanceof IntegerValue) {
      return convertToWrapper(context, (IntegerValue)result, "java.lang.Integer");
    }
    if (result instanceof LongValue) {
      return convertToWrapper(context, (LongValue)result, "java.lang.Long");
    }
    if (result instanceof FloatValue) {
      return convertToWrapper(context, (FloatValue)result, "java.lang.Float");
    }
    if (result instanceof DoubleValue) {
      return convertToWrapper(context, (DoubleValue)result, "java.lang.Double");
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
      methods = wrapperClass.methodsByName("<init>", methodSignature);
    }
    if (methods.size() == 0) {
      throw new EvaluateException("Cannot construct wrapper object for value of type " + value.type() + ": Unable to find either valueOf() or constructor method");
    }
    
    final Method factoryMethod = methods.get(0);

    final ArrayList args = new ArrayList();
    args.add(value);
    
    return process.invokeMethod(context, wrapperClass, factoryMethod, args);
  }
}
