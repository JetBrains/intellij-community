/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.openapi.util.Couple;
import com.intellij.util.containers.HashMap;
import com.sun.jdi.ClassType;
import com.sun.jdi.Method;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.Value;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Eugene Zhuravlev
 *         Date: Feb 8, 2010
 */
public class UnBoxingEvaluator implements Evaluator{
  private final Evaluator myOperand;
  private static final Map<String, Couple<String>> TYPES_TO_CONVERSION_METHOD_MAP = new HashMap<String, Couple<String>>();
  static {
    TYPES_TO_CONVERSION_METHOD_MAP.put("java.lang.Boolean", Couple.of("booleanValue", "()Z"));
    TYPES_TO_CONVERSION_METHOD_MAP.put("java.lang.Byte", Couple.of("byteValue", "()B"));
    TYPES_TO_CONVERSION_METHOD_MAP.put("java.lang.Character", Couple.of("charValue", "()C"));
    TYPES_TO_CONVERSION_METHOD_MAP.put("java.lang.Short", Couple.of("shortValue", "()S"));
    TYPES_TO_CONVERSION_METHOD_MAP.put("java.lang.Integer", Couple.of("intValue", "()I"));
    TYPES_TO_CONVERSION_METHOD_MAP.put("java.lang.Long", Couple.of("longValue", "()J"));
    TYPES_TO_CONVERSION_METHOD_MAP.put("java.lang.Float", Couple.of("floatValue", "()F"));
    TYPES_TO_CONVERSION_METHOD_MAP.put("java.lang.Double", Couple.of("doubleValue", "()D"));
  }

  public static boolean isTypeUnboxable(String typeName) {
    return TYPES_TO_CONVERSION_METHOD_MAP.containsKey(typeName);
  }

  public UnBoxingEvaluator(Evaluator operand) {
    myOperand = new DisableGC(operand);
  }

  public Object evaluate(EvaluationContextImpl context) throws EvaluateException {
    final Value result = (Value)myOperand.evaluate(context);
    if (result == null) {
      throw new EvaluateException("java.lang.NullPointerException: cannot unbox null value");
    }
    if (result instanceof ObjectReference) {
      final String valueTypeName = result.type().name();
      final Couple<String> pair = TYPES_TO_CONVERSION_METHOD_MAP.get(valueTypeName);
      if (pair != null) {
        return convertToPrimitive(context, (ObjectReference)result, pair.getFirst(), pair.getSecond());
      }
    }
    return result;
  }
                                          
  @Nullable
  public Modifier getModifier() {
    return null;
  }

  private static Value convertToPrimitive(EvaluationContextImpl context, ObjectReference value, final String conversionMethodName,
                                          String conversionMethodSignature) throws EvaluateException {
    final DebugProcessImpl process = context.getDebugProcess();
    final ClassType wrapperClass = (ClassType)value.referenceType();
    final List<Method> methods = wrapperClass.methodsByName(conversionMethodName, conversionMethodSignature);
    if (methods.size() == 0) { 
      throw new EvaluateException("Cannot convert to primitive value of type " + value.type() + ": Unable to find method " +
                                  conversionMethodName + conversionMethodSignature);
    }

    final Method method = methods.get(0);

    return process.invokeMethod(context, value, method, new ArrayList());
  }
}