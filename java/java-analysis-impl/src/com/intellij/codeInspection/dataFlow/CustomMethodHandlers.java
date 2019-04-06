/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.codeInspection.dataFlow.value.DfaConstValue;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.util.OptionalUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ReflectionUtil;
import com.siyeh.ig.callMatcher.CallMapper;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import static com.intellij.psi.CommonClassNames.*;
import static com.siyeh.ig.callMatcher.CallMatcher.*;

class CustomMethodHandlers {
  private static final CallMatcher CONSTANT_CALLS = anyOf(
    exactInstanceCall(JAVA_LANG_STRING, "contains", "indexOf", "startsWith", "endsWith", "lastIndexOf", "length", "trim",
                 "substring", "equals", "equalsIgnoreCase", "charAt", "codePointAt", "compareTo", "replace"),
    staticCall(JAVA_LANG_STRING, "valueOf").parameterCount(1),
    staticCall(JAVA_LANG_MATH, "abs", "sqrt", "min", "max"),
    staticCall(JAVA_LANG_INTEGER, "toString", "toBinaryString", "toHexString", "toOctalString", "toUnsignedString").parameterTypes("int"),
    staticCall(JAVA_LANG_LONG, "toString", "toBinaryString", "toHexString", "toOctalString", "toUnsignedString").parameterTypes("long"),
    staticCall(JAVA_LANG_DOUBLE, "toString", "toHexString").parameterTypes("double"),
    staticCall(JAVA_LANG_FLOAT, "toString", "toHexString").parameterTypes("float"),
    staticCall(JAVA_LANG_BYTE, "toString").parameterTypes("byte"),
    staticCall(JAVA_LANG_SHORT, "toString").parameterTypes("short")
  );
  private static final int MAX_STRING_CONSTANT_LENGTH_TO_TRACK = 1024;

  interface CustomMethodHandler {

    @Nullable
    DfaValue getMethodResult(DfaCallArguments callArguments, DfaMemoryState memState, DfaValueFactory factory);

    default CustomMethodHandler compose(CustomMethodHandler other) {
      if (other == null) return this;
      return (args, memState, factory) -> {
        DfaValue result = this.getMethodResult(args, memState, factory);
        return result == null ? other.getMethodResult(args, memState, factory) : result;
      };
    }

  }
  private static final CallMapper<CustomMethodHandler> CUSTOM_METHOD_HANDLERS = new CallMapper<CustomMethodHandler>()
    .register(instanceCall(JAVA_LANG_STRING, "indexOf", "lastIndexOf"),
              (args, memState, factory) -> indexOf(args.myQualifier, memState, factory, SpecialField.STRING_LENGTH))
    .register(instanceCall(JAVA_UTIL_LIST, "indexOf", "lastIndexOf"),
              (args, memState, factory) -> indexOf(args.myQualifier, memState, factory, SpecialField.COLLECTION_SIZE))
    .register(staticCall(JAVA_LANG_MATH, "abs").parameterTypes("int"),
              (args, memState, factory) -> mathAbs(args.myArguments, memState, factory, false))
    .register(staticCall(JAVA_LANG_MATH, "abs").parameterTypes("long"),
              (args, memState, factory) -> mathAbs(args.myArguments, memState, factory, true))
    .register(OptionalUtil.OPTIONAL_OF_NULLABLE,
              (args, memState, factory) -> ofNullable(args.myArguments[0], memState, factory))
    .register(instanceCall(JAVA_UTIL_CALENDAR, "get").parameterTypes("int"),
              (args, memState, factory) -> calendarGet(args.myArguments, memState, factory))
    .register(anyOf(instanceCall("java.io.InputStream", "skip").parameterTypes("long"),
                    instanceCall("java.io.Reader", "skip").parameterTypes("long")),
              (args, memState, factory) -> skip(args.myArguments, memState, factory))
    .register(staticCall(JAVA_LANG_INTEGER, "toHexString").parameterCount(1),
              (args, memState, factory) -> numberAsString(args, memState, factory, 4, Integer.SIZE))
    .register(staticCall(JAVA_LANG_INTEGER, "toOctalString").parameterCount(1),
              (args, memState, factory) -> numberAsString(args, memState, factory, 3, Integer.SIZE))
    .register(staticCall(JAVA_LANG_INTEGER, "toBinaryString").parameterCount(1),
              (args, memState, factory) -> numberAsString(args, memState, factory, 1, Integer.SIZE))
    .register(staticCall(JAVA_LANG_LONG, "toHexString").parameterCount(1),
              (args, memState, factory) -> numberAsString(args, memState, factory, 4, Long.SIZE))
    .register(staticCall(JAVA_LANG_LONG, "toOctalString").parameterCount(1),
              (args, memState, factory) -> numberAsString(args, memState, factory, 3, Long.SIZE))
    .register(staticCall(JAVA_LANG_LONG, "toBinaryString").parameterCount(1),
              (args, memState, factory) -> numberAsString(args, memState, factory, 1, Long.SIZE));

  public static CustomMethodHandler find(PsiMethod method) {
    CustomMethodHandler handler = null;
    if (isConstantCall(method)) {
      handler = (args, memState, factory) -> handleConstantCall(args, memState, factory, method);
    }
    CustomMethodHandler handler2 = CUSTOM_METHOD_HANDLERS.mapFirst(method);
    return handler == null ? handler2 : handler.compose(handler2);
  }

  @Contract("null -> false")
  private static boolean isConstantCall(PsiMethod method) {
    return CONSTANT_CALLS.methodMatches(method);
  }

  @Nullable
  private static DfaValue handleConstantCall(DfaCallArguments arguments, DfaMemoryState state,
                                             DfaValueFactory factory, PsiMethod method) {
    PsiType returnType = method.getReturnType();
    if (returnType == null) return null;
    List<Object> args = new ArrayList<>();
    Object qualifierValue = null;
    if (!method.hasModifierProperty(PsiModifier.STATIC)) {
      qualifierValue = getConstantValue(state, arguments.myQualifier);
      if (qualifierValue == null) return null;
    }
    for (DfaValue argument : arguments.myArguments) {
      Object argumentValue = getConstantValue(state, argument);
      if (argumentValue == null) return null;
      if (argumentValue instanceof Long) {
        long longValue = ((Long)argumentValue).longValue();
        if (longValue >= Integer.MIN_VALUE && longValue <= Integer.MAX_VALUE) {
          argumentValue = (int)longValue;
        }
      }
      args.add(argumentValue);
    }
    Method jvmMethod = toJvmMethod(method);
    if (jvmMethod == null) return null;
    Object result;
    try {
      result = jvmMethod.invoke(qualifierValue, args.toArray());
    }
    catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
      return null;
    }
    return factory.getConstFactory().createFromValue(result, returnType);
  }

  private static Method toJvmMethod(PsiMethod method) {
    return CachedValuesManager.getCachedValue(method, new CachedValueProvider<Method>() {
      @NotNull
      @Override
      public Result<Method> compute() {
        Method reflection = getMethod();
        return Result.create(reflection, method);
      }

      private Class<?> toJvmType(PsiType type) {
        if (TypeUtils.isJavaLangString(type)) {
          return String.class;
        }
        if (TypeUtils.isJavaLangObject(type)) {
          return Object.class;
        }
        if (TypeUtils.typeEquals("java.lang.CharSequence", type)) {
          return CharSequence.class;
        }
        if (PsiType.INT.equals(type)) {
          return int.class;
        }
        if (PsiType.BOOLEAN.equals(type)) {
          return boolean.class;
        }
        if (PsiType.CHAR.equals(type)) {
          return char.class;
        }
        if (PsiType.LONG.equals(type)) {
          return long.class;
        }
        if (PsiType.FLOAT.equals(type)) {
          return float.class;
        }
        if (PsiType.DOUBLE.equals(type)) {
          return double.class;
        }
        return null;
      }

      @Nullable
      private Method getMethod() {
        PsiClass aClass = method.getContainingClass();
        Class<?> containingClass;
        if (aClass == null) return null;
        try {
          containingClass = Class.forName(aClass.getQualifiedName());
        }
        catch (ClassNotFoundException ignored) {
          return null;
        }
        PsiParameter[] parameters = method.getParameterList().getParameters();
        Class<?>[] parameterTypes = new Class[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
          PsiParameter parameter = parameters[i];
          PsiType type = parameter.getType();
          Class<?> jvmType = toJvmType(type);
          if (jvmType == null) return null;
          parameterTypes[i] = jvmType;
        }
        return ReflectionUtil.getMethod(containingClass, method.getName(), parameterTypes);
      }
    });
  }

  private static DfaValue indexOf(DfaValue qualifier,
                                  DfaMemoryState memState,
                                  DfaValueFactory factory,
                                  SpecialField specialField) {
    DfaValue length = specialField.createValue(factory, qualifier);
    LongRangeSet range = memState.getValueFact(length, DfaFactType.RANGE);
    long maxLen = range == null || range.isEmpty() ? Integer.MAX_VALUE : range.max();
    return factory.getFactValue(DfaFactType.RANGE, LongRangeSet.range(-1, maxLen - 1));
  }

  private static DfaValue ofNullable(DfaValue argument, DfaMemoryState state, DfaValueFactory factory) {
    if (state.isNull(argument)) {
      return DfaOptionalSupport.getOptionalValue(factory, false);
    }
    if (state.isNotNull(argument)) {
      return DfaOptionalSupport.getOptionalValue(factory, true);
    }
    return null;
  }

  private static DfaValue mathAbs(DfaValue[] args, DfaMemoryState memState, DfaValueFactory factory, boolean isLong) {
    DfaValue arg = ArrayUtil.getFirstElement(args);
    if (arg == null) return null;
    LongRangeSet range = memState.getValueFact(arg, DfaFactType.RANGE);
    if (range == null) return null;
    return factory.getFactValue(DfaFactType.RANGE, range.abs(isLong));
  }

  private static DfaValue calendarGet(DfaValue[] arguments, DfaMemoryState state, DfaValueFactory factory) {
    if (arguments.length != 1) return null;
    DfaConstValue arg = state.getConstantValue(arguments[0]);
    if (arg == null || !(arg.getValue() instanceof Long)) return null;
    LongRangeSet range = null;
    switch (((Long)arg.getValue()).intValue()) {
      case Calendar.DATE: range = LongRangeSet.range(1, 31); break;
      case Calendar.MONTH: range = LongRangeSet.range(0, 12); break;
      case Calendar.AM_PM: range = LongRangeSet.range(0, 1); break;
      case Calendar.DAY_OF_YEAR: range = LongRangeSet.range(1, 366); break;
      case Calendar.HOUR: range = LongRangeSet.range(0, 11); break;
      case Calendar.HOUR_OF_DAY: range = LongRangeSet.range(0, 23); break;
      case Calendar.MINUTE:
      case Calendar.SECOND: range = LongRangeSet.range(0, 59); break;
      case Calendar.MILLISECOND: range = LongRangeSet.range(0, 999); break;
    }
    return range == null ? null : factory.getFactValue(DfaFactType.RANGE, range);
  }

  private static DfaValue skip(DfaValue[] arguments, DfaMemoryState state, DfaValueFactory factory) {
    if (arguments.length != 1) return null;
    LongRangeSet range = state.getValueFact(arguments[0], DfaFactType.RANGE);
    if (range == null || range.isEmpty()) return null;
    return factory.getFactValue(DfaFactType.RANGE, LongRangeSet.range(0, Math.max(0, range.max())));
  }


  private static DfaValue numberAsString(DfaCallArguments args, DfaMemoryState state, DfaValueFactory factory, int bitsPerChar,
                                         int maxBits) {
    DfaValue arg = args.myArguments[0];
    if (arg == null) return null;
    LongRangeSet range = state.getValueFact(arg, DfaFactType.RANGE);
    if (range == null || range.isEmpty()) return null;
    int usedBits = range.min() >= 0 ? Long.SIZE - Long.numberOfLeadingZeros(range.max()) : maxBits;
    int max = Math.max(1, (usedBits - 1) / bitsPerChar + 1);
    DfaValue lengthRange = factory.getFactValue(DfaFactType.RANGE, LongRangeSet.range(1, max));
    DfaFactMap map = DfaFactMap.EMPTY.with(DfaFactType.NULLABILITY, DfaNullability.NOT_NULL)
      .with(DfaFactType.SPECIAL_FIELD_VALUE, SpecialField.STRING_LENGTH.withValue(lengthRange));
    return factory.getFactFactory().createValue(map);
  }

  private static Object getConstantValue(DfaMemoryState memoryState, DfaValue value) {
    if (value != null) {
      LongRangeSet fact = memoryState.getValueFact(value, DfaFactType.RANGE);
      Long constantValue = fact == null ? null : fact.getConstantValue();
      if (constantValue != null) {
        return constantValue;
      }
    }
    DfaConstValue dfaConst = memoryState.getConstantValue(value);
    if (dfaConst != null) {
      Object constant = dfaConst.getValue();
      if (constant instanceof String && ((String)constant).length() > MAX_STRING_CONSTANT_LENGTH_TO_TRACK) return null;
      return constant;
    }
    return null;
  }
}
