// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.codeInspection.dataFlow.types.DfConstantType;
import com.intellij.codeInspection.dataFlow.types.DfIntType;
import com.intellij.codeInspection.dataFlow.types.DfLongType;
import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.RelationType;
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
import java.util.Locale;

import static com.intellij.codeInspection.dataFlow.SpecialField.*;
import static com.intellij.codeInspection.dataFlow.types.DfTypes.*;
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
    staticCall(JAVA_LANG_SHORT, "toString").parameterTypes("short"),
    staticCall(JAVA_LANG_BOOLEAN, "parseBoolean").parameterTypes("java.lang.String"),
    staticCall(JAVA_LANG_INTEGER, "compare", "compareUnsigned").parameterTypes("int", "int"),
    staticCall(JAVA_LANG_LONG, "compare", "compareUnsigned").parameterTypes("long", "long"),
    staticCall(JAVA_LANG_DOUBLE, "compare").parameterTypes("double", "double"),
    staticCall(JAVA_LANG_FLOAT, "compare").parameterTypes("float", "float"),
    staticCall(JAVA_LANG_BYTE, "compare", "compareUnsigned").parameterTypes("byte", "byte"),
    staticCall(JAVA_LANG_SHORT, "compare", "compareUnsigned").parameterTypes("short", "short"),
    staticCall(JAVA_LANG_BOOLEAN, "compare").parameterTypes("boolean", "boolean"),
    exactInstanceCall(JAVA_LANG_INTEGER, "toString").parameterCount(0),
    exactInstanceCall(JAVA_LANG_LONG, "toString").parameterCount(0),
    exactInstanceCall(JAVA_LANG_DOUBLE, "toString").parameterCount(0),
    exactInstanceCall(JAVA_LANG_FLOAT, "toString").parameterCount(0),
    exactInstanceCall(JAVA_LANG_BYTE, "toString").parameterCount(0),
    exactInstanceCall(JAVA_LANG_SHORT, "toString").parameterCount(0),
    exactInstanceCall(JAVA_LANG_CHARACTER, "toString").parameterCount(0),
    exactInstanceCall(JAVA_LANG_BOOLEAN, "toString").parameterCount(0)
  );
  static final int MAX_STRING_CONSTANT_LENGTH_TO_TRACK = 256;

  interface CustomMethodHandler {

    @NotNull
    DfType getMethodResult(DfaCallArguments callArguments,
                           DfaMemoryState memState,
                           DfaValueFactory factory,
                           PsiMethod method);

    default CustomMethodHandler compose(CustomMethodHandler other) {
      if (other == null) return this;
      return (args, memState, factory, method) -> {
        DfType result = this.getMethodResult(args, memState, factory, method);
        return result == TOP ? other.getMethodResult(args, memState, factory, method) : result;
      };
    }
  }

  private static final CallMapper<CustomMethodHandler> CUSTOM_METHOD_HANDLERS = new CallMapper<CustomMethodHandler>()
    .register(instanceCall(JAVA_LANG_STRING, "indexOf", "lastIndexOf"),
              (args, memState, factory, method) -> indexOf(args.myQualifier, memState, factory, STRING_LENGTH))
    .register(instanceCall(JAVA_UTIL_LIST, "indexOf", "lastIndexOf"),
              (args, memState, factory, method) -> indexOf(args.myQualifier, memState, factory, COLLECTION_SIZE))
    .register(staticCall(JAVA_LANG_MATH, "abs").parameterTypes("int"),
              (args, memState, factory, method) -> mathAbs(args.myArguments, memState, false))
    .register(staticCall(JAVA_LANG_MATH, "abs").parameterTypes("long"),
              (args, memState, factory, method) -> mathAbs(args.myArguments, memState, true))
    .register(exactInstanceCall(JAVA_LANG_STRING, "substring"),
              (args, memState, factory, method) -> substring(args, memState, factory, method.getReturnType()))
    .register(OptionalUtil.OPTIONAL_OF_NULLABLE,
              (args, memState, factory, method) -> OPTIONAL_VALUE.asDfType(memState.getDfType(args.myArguments[0]), method.getReturnType()))
    .register(instanceCall(JAVA_UTIL_CALENDAR, "get").parameterTypes("int"),
              (args, memState, factory, method) -> calendarGet(args.myArguments, memState))
    .register(anyOf(instanceCall("java.io.InputStream", "skip").parameterTypes("long"),
                    instanceCall("java.io.Reader", "skip").parameterTypes("long")),
              (args, memState, factory, method) -> skip(args.myArguments, memState))
    .register(staticCall(JAVA_LANG_INTEGER, "toHexString").parameterCount(1),
              (args, memState, factory, method) -> numberAsString(args, memState, 4, Integer.SIZE))
    .register(staticCall(JAVA_LANG_INTEGER, "toOctalString").parameterCount(1),
              (args, memState, factory, method) -> numberAsString(args, memState, 3, Integer.SIZE))
    .register(staticCall(JAVA_LANG_INTEGER, "toBinaryString").parameterCount(1),
              (args, memState, factory, method) -> numberAsString(args, memState, 1, Integer.SIZE))
    .register(staticCall(JAVA_LANG_LONG, "toHexString").parameterCount(1),
              (args, memState, factory, method) -> numberAsString(args, memState, 4, Long.SIZE))
    .register(staticCall(JAVA_LANG_LONG, "toOctalString").parameterCount(1),
              (args, memState, factory, method) -> numberAsString(args, memState, 3, Long.SIZE))
    .register(staticCall(JAVA_LANG_LONG, "toBinaryString").parameterCount(1),
              (args, memState, factory, method) -> numberAsString(args, memState, 1, Long.SIZE))
    .register(instanceCall(JAVA_LANG_ENUM, "name").parameterCount(0),
              (args, memState, factory, method) -> enumName(args.myQualifier, memState, method.getReturnType()))
    .register(staticCall(JAVA_UTIL_COLLECTIONS, "emptyList", "emptySet", "emptyMap").parameterCount(0),
              (args, memState, factory, method) -> getEmptyCollectionConstant(method))
    .register(exactInstanceCall(JAVA_LANG_CLASS, "getName", "getSimpleName").parameterCount(0),
              (args, memState, factory, method) -> className(memState, args.myQualifier, method.getName(), method.getReturnType()))
    .register(anyOf(
      staticCall(JAVA_UTIL_COLLECTIONS, "singleton", "singletonList", "singletonMap"),
      staticCall(JAVA_UTIL_LIST, "of"),
      staticCall(JAVA_UTIL_SET, "of"),
      staticCall(JAVA_UTIL_MAP, "of", "ofEntries"),
      staticCall(JAVA_UTIL_ARRAYS, "asList")), CustomMethodHandlers::collectionFactory)
    .register(anyOf(
      staticCall(JAVA_LANG_INTEGER, "compare").parameterTypes("int", "int"),
      staticCall(JAVA_LANG_LONG, "compare").parameterTypes("long", "long"),
      staticCall(JAVA_LANG_BYTE, "compare").parameterTypes("byte", "byte"),
      staticCall(JAVA_LANG_SHORT, "compare").parameterTypes("short", "short")),
              (args, state, factory, method) -> compareInteger(args, state))
    .register(anyOf(
      instanceCall("java.util.Random", "nextInt").parameterTypes("int"),
      instanceCall("java.util.SplittableRandom", "nextInt").parameterTypes("int"),
      instanceCall("java.util.SplittableRandom", "nextInt").parameterTypes("int", "int")), CustomMethodHandlers::randomNextInt);

  public static CustomMethodHandler find(PsiMethod method) {
    CustomMethodHandler handler = null;
    if (isConstantCall(method)) {
      handler = (arguments, state, factory, m) -> handleConstantCall(arguments, state, m);
    }
    CustomMethodHandler handler2 = CUSTOM_METHOD_HANDLERS.mapFirst(method);
    return handler == null ? handler2 : handler.compose(handler2);
  }

  @Contract("null -> false")
  private static boolean isConstantCall(PsiMethod method) {
    return CONSTANT_CALLS.methodMatches(method);
  }

  private static @NotNull DfType handleConstantCall(DfaCallArguments arguments, DfaMemoryState state, PsiMethod method) {
    PsiType returnType = method.getReturnType();
    if (returnType == null) return TOP;
    List<Object> args = new ArrayList<>();
    Object qualifierValue = null;
    if (!method.hasModifierProperty(PsiModifier.STATIC)) {
      qualifierValue = getConstantValue(state, arguments.myQualifier);
      if (qualifierValue == null) return TOP;
    }
    for (DfaValue argument : arguments.myArguments) {
      Object argumentValue = getConstantValue(state, argument);
      if (argumentValue == null) return TOP;
      if (argumentValue instanceof Long) {
        long longValue = ((Long)argumentValue).longValue();
        if (longValue >= Integer.MIN_VALUE && longValue <= Integer.MAX_VALUE) {
          argumentValue = (int)longValue;
        }
      }
      args.add(argumentValue);
    }
    Method jvmMethod = toJvmMethod(method);
    if (jvmMethod == null) return TOP;
    Object result;
    try {
      result = jvmMethod.invoke(qualifierValue, args.toArray());
    }
    catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
      return TOP;
    }
    return constant(result, returnType);
  }

  private static Method toJvmMethod(PsiMethod method) {
    return CachedValuesManager.getCachedValue(method, new CachedValueProvider<Method>() {
      @Override
      public @NotNull Result<Method> compute() {
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
        if (TypeUtils.typeEquals(JAVA_LANG_CHAR_SEQUENCE, type)) {
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

      private @Nullable Method getMethod() {
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

  private static @NotNull DfType indexOf(DfaValue qualifier,
                                         DfaMemoryState memState,
                                         DfaValueFactory factory,
                                         SpecialField specialField) {
    DfaValue length = specialField.createValue(factory, qualifier);
    LongRangeSet range = DfIntType.extractRange(memState.getDfType(length));
    return intRange(LongRangeSet.range(-1, range.max() - 1));
  }

  private static @NotNull DfType collectionFactory(DfaCallArguments args,
                                                   DfaMemoryState memState, DfaValueFactory factory,
                                                   PsiMethod method) {
    PsiType type = method.getReturnType();
    if (!(type instanceof PsiClassType)) return TOP;
    int factor = ((PsiClassType)type).rawType().equalsToText(JAVA_UTIL_MAP) ? 2 : 1;
    DfType size;
    if (method.isVarArgs()) {
      size = memState.getDfType(ARRAY_LENGTH.createValue(factory, args.myArguments[0]));
    }
    else {
      size = intValue(args.myArguments.length / factor);
    }
    boolean asList = method.getName().equals("asList");
    Mutability mutability = asList ? Mutability.MUTABLE : Mutability.UNMODIFIABLE;
    DfType result = typedObject(type, Nullability.NOT_NULL)
      .meet(COLLECTION_SIZE.asDfType(size))
      .meet(mutability.asDfType());
    return asList ? result.meet(LOCAL_OBJECT) : result;
  }

  private static DfType getEmptyCollectionConstant(PsiMethod method) {
    String fieldName = "EMPTY_" + method.getName().substring("empty".length()).toUpperCase(Locale.ROOT);
    PsiClass collectionsClass = method.getContainingClass();
    if (collectionsClass == null) return TOP;
    PsiField field = collectionsClass.findFieldByName(fieldName, false);
    if (field == null) return TOP;
    return constant(field, field.getType());
  }

  private static @NotNull DfType substring(DfaCallArguments args, DfaMemoryState state, DfaValueFactory factory, PsiType stringType) {
    if (stringType == null || !stringType.equalsToText(JAVA_LANG_STRING)) return TOP;
    DfaValue qualifier = args.myQualifier;
    DfaValue[] arguments = args.myArguments;
    if (arguments.length < 1 || arguments.length > 2 || arguments[0] == null) return TOP;
    DfaValue from = arguments[0];
    DfaValue lenVal = STRING_LENGTH.createValue(factory, qualifier);
    DfaValue to = arguments.length == 1 ? lenVal : arguments[1];
    DfaValue resultLenVal = factory.getBinOpFactory().create(to, from, state, false, JavaTokenType.MINUS);
    DfType resultLen = state.getDfType(resultLenVal);
    if (!(resultLen instanceof DfIntType)) return FAIL;
    resultLen = ((DfIntType)resultLen).meetRelation(RelationType.GE, intValue(0));
    if (!(resultLen instanceof DfIntType)) return FAIL;
    resultLen = ((DfIntType)resultLen).meetRelation(RelationType.LE, state.getDfType(lenVal));
    return STRING_LENGTH.asDfType(resultLen, stringType);
  }

  private static @NotNull DfType mathAbs(DfaValue[] args, DfaMemoryState memState, boolean isLong) {
    DfaValue arg = ArrayUtil.getFirstElement(args);
    if (arg == null) return TOP;
    DfType type = memState.getDfType(arg);
    LongRangeSet range = isLong ? DfLongType.extractRange(type) : DfIntType.extractRange(type);
    return isLong ? longRange(range.abs(true)) : intRange(range.abs(false));
  }

  private static @NotNull DfType calendarGet(DfaValue[] arguments, DfaMemoryState state) {
    if (arguments.length != 1) return TOP;
    Integer val = DfConstantType.getConstantOfType(state.getDfType(arguments[0]), Integer.class);
    if (val == null) return TOP;
    LongRangeSet range = null;
    switch (val) {
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
    return range == null ? TOP : intRange(range);
  }

  private static @NotNull DfType skip(DfaValue[] arguments, DfaMemoryState state) {
    if (arguments.length != 1) return TOP;
    LongRangeSet range = DfLongType.extractRange(state.getDfType(arguments[0]));
    return longRange(LongRangeSet.range(0, Math.max(0, range.max())));
  }

  private static @NotNull DfType numberAsString(DfaCallArguments args, DfaMemoryState state, int bitsPerChar, int maxBits) {
    DfaValue arg = args.myArguments[0];
    if (arg == null) return TOP;
    LongRangeSet range = DfLongType.extractRange(state.getDfType(arg));
    int usedBits = range.min() >= 0 ? Long.SIZE - Long.numberOfLeadingZeros(range.max()) : maxBits;
    int max = Math.max(1, (usedBits - 1) / bitsPerChar + 1);
    return STRING_LENGTH.asDfType(intRange(LongRangeSet.range(1, max)));
  }

  private static @NotNull DfType enumName(DfaValue qualifier, DfaMemoryState state, PsiType type) {
    DfType dfType = state.getDfType(qualifier);
    PsiEnumConstant value = DfConstantType.getConstantOfType(dfType, PsiEnumConstant.class);
    if (value != null) {
      return constant(value.getName(), type);
    }
    return TOP;
  }

  private static Object getConstantValue(DfaMemoryState memoryState, DfaValue value) {
    DfType type = memoryState.getUnboxedDfType(value);
    Object constant = DfConstantType.getConstantOfType(type, Object.class);
    if (constant instanceof String && ((String)constant).length() > MAX_STRING_CONSTANT_LENGTH_TO_TRACK) return null;
    return constant;
  }

  private static @NotNull DfType randomNextInt(DfaCallArguments arguments, DfaMemoryState state, DfaValueFactory factory, PsiMethod method) {
    DfaValue[] values = arguments.myArguments;
    if (values == null) return TOP;
    LongRangeSet fromLowerBound;
    LongRangeSet fromUpperBound;
    if (values.length == 1) {
      fromLowerBound = LongRangeSet.range(0, Integer.MAX_VALUE - 1);
      fromUpperBound = DfIntType.extractRange(state.getDfType(values[0])).fromRelation(RelationType.LT);
    } else if (values.length == 2){
      fromLowerBound = DfIntType.extractRange(state.getDfType(values[0])).fromRelation(RelationType.GE);
      fromUpperBound = DfIntType.extractRange(state.getDfType(values[1])).fromRelation(RelationType.LT);
    } else return TOP;
    LongRangeSet intersection = fromLowerBound.intersect(fromUpperBound);
    return intRangeClamped(intersection);
  }

  private static @NotNull DfType className(DfaMemoryState memState,
                                           DfaValue qualifier,
                                           String name,
                                           PsiType stringType) {
    PsiClassType type = DfConstantType.getConstantOfType(memState.getDfType(qualifier), PsiClassType.class);
    if (type != null) {
      PsiClass psiClass = type.resolve();
      if (psiClass != null) {
        return constant(name.equals("getSimpleName") ? psiClass.getName() : psiClass.getQualifiedName(), stringType);
      }
    }
    return TOP;
  }

  private static DfType compareInteger(DfaCallArguments args, DfaMemoryState state) {
    DfaValue[] arguments = args.myArguments;
    if (arguments.length != 2) return TOP;
    RelationType relation = state.getRelation(arguments[0], arguments[1]);
    if (relation == null) {
      LongRangeSet left = DfLongType.extractRange(state.getDfType(arguments[0]));
      LongRangeSet right = DfLongType.extractRange(state.getDfType(arguments[1]));
      if (left.isEmpty() || right.isEmpty()) return BOTTOM;
      if (left.max() < right.min()) {
        relation = RelationType.LT;
      }
      else if (left.max() <= right.min()) {
        relation = RelationType.LE;
      }
      else if (left.min() > right.max()) {
        relation = RelationType.GT;
      }
      else if (left.min() >= right.max()) {
        relation = RelationType.GE;
      }
      else if (!left.intersects(right)) {
        relation = RelationType.NE;
      }
      else if (left.getConstantValue() != null && left.equals(right)) {
        relation = RelationType.EQ;
      }
    }
    if (relation != null) {
      return intRangeClamped(LongRangeSet.point(0).fromRelation(relation));
    }
    return TOP;
  }
}
