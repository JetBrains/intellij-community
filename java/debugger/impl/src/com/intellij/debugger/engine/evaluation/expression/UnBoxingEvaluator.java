// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Couple;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.impl.PsiJavaParserFacadeImpl;
import com.sun.jdi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Eugene Zhuravlev
 */
public class UnBoxingEvaluator implements Evaluator {
  private static final Logger LOG = Logger.getInstance(UnBoxingEvaluator.class);

  private final Evaluator myOperand;
  private static final Map<String, Couple<String>> TYPES_TO_CONVERSION_METHOD_MAP = new HashMap<>();
  static {
    TYPES_TO_CONVERSION_METHOD_MAP.put(CommonClassNames.JAVA_LANG_BOOLEAN, Couple.of("booleanValue", "()Z"));
    TYPES_TO_CONVERSION_METHOD_MAP.put(CommonClassNames.JAVA_LANG_BYTE, Couple.of("byteValue", "()B"));
    TYPES_TO_CONVERSION_METHOD_MAP.put(CommonClassNames.JAVA_LANG_CHARACTER, Couple.of("charValue", "()C"));
    TYPES_TO_CONVERSION_METHOD_MAP.put(CommonClassNames.JAVA_LANG_SHORT, Couple.of("shortValue", "()S"));
    TYPES_TO_CONVERSION_METHOD_MAP.put(CommonClassNames.JAVA_LANG_INTEGER, Couple.of("intValue", "()I"));
    TYPES_TO_CONVERSION_METHOD_MAP.put(CommonClassNames.JAVA_LANG_LONG, Couple.of("longValue", "()J"));
    TYPES_TO_CONVERSION_METHOD_MAP.put(CommonClassNames.JAVA_LANG_FLOAT, Couple.of("floatValue", "()F"));
    TYPES_TO_CONVERSION_METHOD_MAP.put(CommonClassNames.JAVA_LANG_DOUBLE, Couple.of("doubleValue", "()D"));
  }

  public static boolean isTypeUnboxable(String typeName) {
    return TYPES_TO_CONVERSION_METHOD_MAP.containsKey(typeName);
  }

  public UnBoxingEvaluator(@NotNull Evaluator operand) {
    myOperand = DisableGC.create(operand);
  }

  @Override
  public Object evaluate(EvaluationContextImpl context) throws EvaluateException {
    return unbox(myOperand.evaluate(context), context);
  }

  public static Object unbox(@Nullable Object value, EvaluationContextImpl context) throws EvaluateException {
    if (value == null) {
      throw new EvaluateException("java.lang.NullPointerException: cannot unbox null value");
    }
    if (value instanceof ObjectReference) {
      final String valueTypeName = ((ObjectReference)value).type().name();
      final Couple<String> pair = TYPES_TO_CONVERSION_METHOD_MAP.get(valueTypeName);
      if (pair != null) {
        return convertToPrimitive(context, (ObjectReference)value, pair.getFirst(), pair.getSecond());
      }
    }
    return value;
  }

  private static Value convertToPrimitive(EvaluationContextImpl context, ObjectReference value, final String conversionMethodName,
                                          String conversionMethodSignature) throws EvaluateException {
    // for speedup first try value field
    Value primitiveValue = getInnerPrimitiveValue(value);
    if (primitiveValue != null) {
      return primitiveValue;
    }

    Method method = DebuggerUtils.findMethod(value.referenceType(), conversionMethodName, conversionMethodSignature);
    if (method == null) {
      throw new EvaluateException("Cannot convert to primitive value of type " + value.type() + ": Unable to find method " +
                                  conversionMethodName + conversionMethodSignature);
    }

    return context.getDebugProcess().invokeMethod(context, value, method, Collections.emptyList());
  }

  @Nullable
  public static PrimitiveValue getInnerPrimitiveValue(@Nullable ObjectReference value) {
    if (value != null) {
      ReferenceType type = value.referenceType();
      Field valueField = type.fieldByName("value");
      if (valueField != null) {
        Value primitiveValue = value.getValue(valueField);
        if (primitiveValue instanceof PrimitiveValue) {
          LOG.assertTrue(type.name().equals(PsiJavaParserFacadeImpl.getPrimitiveType(primitiveValue.type().name()).getBoxedTypeName()));
          return (PrimitiveValue)primitiveValue;
        }
      }
    }
    return null;
  }
}