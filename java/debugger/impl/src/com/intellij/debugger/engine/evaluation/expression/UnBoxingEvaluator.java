// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.impl.DebuggerUtilsAsync;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Couple;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.impl.PsiJavaParserFacadeImpl;
import com.intellij.util.containers.ContainerUtil;
import com.sun.jdi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static java.util.concurrent.CompletableFuture.completedFuture;

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
    Value primitiveValue = getInnerPrimitiveValue(value, true).join();
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

  public static CompletableFuture<PrimitiveValue> getInnerPrimitiveValue(@Nullable ObjectReference value, boolean now) {
    if (value != null) {
      ReferenceType type = value.referenceType();
      return fields(type, now)
        .thenCompose(fields -> {
          Field valueField = ContainerUtil.find(fields, f -> "value".equals(f.name()));
          if (valueField != null) {
            return getValue(value, valueField, now)
              .thenApply(primitiveValue -> {
                if (primitiveValue instanceof PrimitiveValue) {
                  String expected = PsiJavaParserFacadeImpl.getPrimitiveType(primitiveValue.type().name()).getBoxedTypeName();
                  String actual = type.name();
                  LOG.assertTrue(actual.equals(expected),
                                 "Unexpected unboxable value type" +
                                 "\nType: " + actual +
                                 "\nPrimitive value type: " + primitiveValue.type() +
                                 "\nBoxed type: " + expected);
                  return (PrimitiveValue)primitiveValue;
                }
                return null;
              });
          }
          return completedFuture(null);
        });
    }
    return completedFuture(null);
  }

  // TODO: need to make normal async join
  private static CompletableFuture<List<Field>> fields(ReferenceType type, boolean now) {
    return now ? completedFuture(type.fields()) : DebuggerUtilsAsync.fields(type);
  }

  private static CompletableFuture<Value> getValue(ObjectReference ref, Field field, boolean now) {
    return now ? completedFuture(ref.getValue(field)) : DebuggerUtilsAsync.getValue(ref, field);
  }
}