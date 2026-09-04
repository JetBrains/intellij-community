// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.impl.DebuggerUtilsAsync;
import com.intellij.lang.jvm.types.JvmPrimitiveTypeKind;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.ContainerUtil;
import com.sun.jdi.Field;
import com.sun.jdi.Method;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.PrimitiveValue;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static java.util.concurrent.CompletableFuture.completedFuture;

/**
 * @author Eugene Zhuravlev
 */
public class UnBoxingEvaluator implements Evaluator {
  private static final Logger LOG = Logger.getInstance(UnBoxingEvaluator.class);

  private final Evaluator myOperand;

  public static boolean isTypeUnboxable(String typeName) {
    return getUnboxableType(typeName) != null;
  }

  public UnBoxingEvaluator(@NotNull Evaluator operand) {
    myOperand = DisableGC.create(operand);
  }

  @Override
  public Object evaluate(EvaluationContextImpl context) throws EvaluateException {
    return unbox(myOperand.evaluate(context), context);
  }

  public static Object unbox(@Nullable Object value, EvaluationContext context) throws EvaluateException {
    if (value == null) {
      throw new EvaluateException("java.lang.NullPointerException: cannot unbox null value");
    }
    if (value instanceof ObjectReference) {
      final String valueTypeName = ((ObjectReference)value).type().name();
      JvmPrimitiveTypeKind primitiveType = getUnboxableType(valueTypeName);
      if (primitiveType != null) {
        return convertToPrimitive(context,
                                  (ObjectReference)value,
                                  primitiveType.getName() + "Value",
                                  "()" + primitiveType.getBinaryName());
      }
    }
    return value;
  }

  private static @Nullable JvmPrimitiveTypeKind getUnboxableType(String typeName) {
    JvmPrimitiveTypeKind primitiveType = JvmPrimitiveTypeKind.getKindByFqn(typeName);
    return primitiveType != JvmPrimitiveTypeKind.VOID ? primitiveType : null;
  }

  private static Value convertToPrimitive(EvaluationContext context, ObjectReference value, final String conversionMethodName,
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
                  JvmPrimitiveTypeKind primitiveType = JvmPrimitiveTypeKind.getKindByName(primitiveValue.type().name());
                  String expected = primitiveType != null ? primitiveType.getBoxedFqn() : null;
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