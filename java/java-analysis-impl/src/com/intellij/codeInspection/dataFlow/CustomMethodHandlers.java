// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInspection.dataFlow.jvm.SpecialField;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeBinOp;
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeType;
import com.intellij.codeInspection.dataFlow.types.DfIntType;
import com.intellij.codeInspection.dataFlow.types.DfLongType;
import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.codeInspection.dataFlow.value.DfaBinOpValue;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.RelationType;
import com.intellij.codeInspection.util.OptionalUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.*;
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

import static com.intellij.codeInspection.dataFlow.jvm.SpecialField.*;
import static com.intellij.codeInspection.dataFlow.types.DfTypes.*;
import static com.intellij.psi.CommonClassNames.*;
import static com.siyeh.ig.callMatcher.CallMatcher.*;

public final class CustomMethodHandlers {
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
  public static final int MAX_STRING_CONSTANT_LENGTH_TO_TRACK = 256;

  public interface CustomMethodHandler {

    @Nullable
    DfaValue getMethodResultValue(DfaCallArguments callArguments,
                                  DfaMemoryState memState,
                                  DfaValueFactory factory,
                                  PsiMethod method);

    default CustomMethodHandler compose(CustomMethodHandler other) {
      if (other == null) return this;
      return (args, memState, factory, method) -> {
        DfaValue result = this.getMethodResultValue(args, memState, factory, method);
        return result == null ? other.getMethodResultValue(args, memState, factory, method) : result;
      };
    }
  }

  interface DfTypeCustomMethodHandler extends CustomMethodHandler {
    @NotNull
    DfType getMethodResult(DfaCallArguments callArguments,
                           DfaMemoryState memState,
                           DfaValueFactory factory,
                           PsiMethod method);

    @Override
    @Nullable
    default DfaValue getMethodResultValue(DfaCallArguments callArguments,
                                  DfaMemoryState memState,
                                  DfaValueFactory factory,
                                  PsiMethod method) {
      DfType dfType = getMethodResult(callArguments, memState, factory, method);
      return dfType == DfType.TOP ? null : factory.fromDfType(dfType);
    }
  }

  private static CustomMethodHandler toValue(DfTypeCustomMethodHandler handler) {
    return handler;
  }

  private static final CallMapper<CustomMethodHandler> CUSTOM_METHOD_HANDLERS = new CallMapper<CustomMethodHandler>()
    .register(instanceCall(JAVA_LANG_STRING, "indexOf", "lastIndexOf"),
              toValue((args, memState, factory, method) -> indexOf(args.myQualifier, memState, factory, STRING_LENGTH)))
    .register(instanceCall(JAVA_UTIL_LIST, "indexOf", "lastIndexOf"),
              toValue((args, memState, factory, method) -> indexOf(args.myQualifier, memState, factory, COLLECTION_SIZE)))
    .register(staticCall(JAVA_LANG_MATH, "abs").parameterTypes("int"),
              toValue((args, memState, factory, method) -> mathAbs(args.myArguments, memState, false)))
    .register(staticCall(JAVA_LANG_MATH, "abs").parameterTypes("long"),
              toValue((args, memState, factory, method) -> mathAbs(args.myArguments, memState, true)))
    .register(exactInstanceCall(JAVA_LANG_STRING, "substring"),
              (args, memState, factory, method) -> substring(args, memState, factory, method.getReturnType()))
    .register(OptionalUtil.OPTIONAL_OF_NULLABLE,
              toValue((args, memState, factory, method) -> OPTIONAL_VALUE.asDfType(memState.getDfType(args.myArguments[0]))))
    .register(instanceCall(JAVA_UTIL_CALENDAR, "get").parameterTypes("int"),
              toValue((args, memState, factory, method) -> calendarGet(args, memState, factory)))
    .register(anyOf(instanceCall("java.io.InputStream", "skip").parameterTypes("long"),
                    instanceCall("java.io.Reader", "skip").parameterTypes("long")),
              toValue((args, memState, factory, method) -> skip(args.myArguments, memState)))
    .register(staticCall(JAVA_LANG_INTEGER, "toHexString").parameterCount(1),
              toValue((args, memState, factory, method) -> numberAsString(args, memState, 4, Integer.SIZE)))
    .register(staticCall(JAVA_LANG_INTEGER, "toOctalString").parameterCount(1),
              toValue((args, memState, factory, method) -> numberAsString(args, memState, 3, Integer.SIZE)))
    .register(staticCall(JAVA_LANG_INTEGER, "toBinaryString").parameterCount(1),
              toValue((args, memState, factory, method) -> numberAsString(args, memState, 1, Integer.SIZE)))
    .register(staticCall(JAVA_LANG_LONG, "toHexString").parameterCount(1),
              toValue((args, memState, factory, method) -> numberAsString(args, memState, 4, Long.SIZE)))
    .register(staticCall(JAVA_LANG_LONG, "toOctalString").parameterCount(1),
              toValue((args, memState, factory, method) -> numberAsString(args, memState, 3, Long.SIZE)))
    .register(staticCall(JAVA_LANG_LONG, "toBinaryString").parameterCount(1),
              toValue((args, memState, factory, method) -> numberAsString(args, memState, 1, Long.SIZE)))
    .register(instanceCall(JAVA_LANG_ENUM, "name").parameterCount(0),
              toValue((args, memState, factory, method) -> enumName(args.myQualifier, memState, method.getReturnType())))
    .register(staticCall(JAVA_UTIL_COLLECTIONS, "emptyList", "emptySet", "emptyMap").parameterCount(0),
              toValue((args, memState, factory, method) -> getEmptyCollectionConstant(method)))
    .register(exactInstanceCall(JAVA_LANG_CLASS, "getName", "getSimpleName", "getCanonicalName").parameterCount(0),
              toValue((args, memState, factory, method) -> className(memState, args.myQualifier, method.getName(), method.getReturnType())))
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
              toValue((args, state, factory, method) -> compareInteger(args, state)))
    .register(anyOf(
      instanceCall("java.util.Random", "nextInt").parameterTypes("int"),
      instanceCall("java.util.SplittableRandom", "nextInt").parameterTypes("int"),
      instanceCall("java.util.SplittableRandom", "nextInt").parameterTypes("int", "int")), toValue(CustomMethodHandlers::randomNextInt))
    .register(staticCall(JAVA_UTIL_ARRAYS, "copyOf"), (arguments, state, factory, method) -> copyOfArray(arguments, factory, method))
    .register(staticCall(JAVA_UTIL_COLLECTIONS, "unmodifiableCollection", "unmodifiableList", "unmodifiableSet", "unmodifiableMap",
                         "unmodifiableSortedSet", "unmodifiableSortedMap", "unmodifiableNavigableSet", "unmodifiableNavigableMap")
                .parameterCount(1), (arguments, state, factory, method) -> unmodifiableView(arguments, factory, method))
    .register(staticCall(JAVA_UTIL_COLLECTIONS, "checkedCollection", "checkedList", "checkedSet", "checkedMap",
                         "checkedSortedSet", "checkedSortedMap", "checkedNavigableSet", "checkedNavigableMap", "checkedQueue",
                         "synchronizedCollection", "synchronizedList", "synchronizedSet", "synchronizedMap",
                         "synchronizedSortedSet", "synchronizedSortedMap", "synchronizedNavigableSet", "synchronizedNavigableMap"),
              (arguments, state, factory, method) -> collectionView(ArrayUtil.getFirstElement(arguments.myArguments), factory, method))
    .register(anyOf(instanceCall(JAVA_UTIL_MAP, "keySet", "values", "entrySet").parameterCount(0),
                    instanceCall("java.util.NavigableSet", "descendingSet").parameterCount(0),
                    instanceCall("java.util.NavigableMap", "descendingMap").parameterCount(0)),
              (arguments, state, factory, method) -> collectionView(arguments.myQualifier, factory, method))
    .register(instanceCall(JAVA_UTIL_COLLECTION, "toArray").parameterTypes("T[]"), CustomMethodHandlers::collectionToArray)
    .register(instanceCall(JAVA_UTIL_COLLECTION, "toArray").parameterCount(0), CustomMethodHandlers::collectionToArray)
    .register(instanceCall(JAVA_LANG_STRING, "toCharArray").parameterCount(0), CustomMethodHandlers::stringToCharArray)
    .register(exactInstanceCall(JAVA_LANG_OBJECT, "getClass").parameterCount(0), toValue(CustomMethodHandlers::objectGetClass))
    .register(anyOf(staticCall(JAVA_LANG_MATH, "random").parameterCount(0),
                    instanceCall("java.util.Random", "nextDouble").parameterCount(0),
                    instanceCall("java.util.SplittableRandom", "nextDouble").parameterCount(0)), 
              toValue((arguments, state, factory, method) -> doubleRange(0.0, Math.nextDown(1.0))))
    .register(instanceCall("java.util.Random", "nextFloat").parameterCount(0), 
              toValue((arguments, state, factory, method) -> floatRange(0.0f, Math.nextDown(1.0f))))
    .register(staticCall(JAVA_LANG_DOUBLE, "isNaN").parameterTypes("double"),
              toValue((arguments, state, factory, method) -> isNaN(arguments, state, DOUBLE_NAN)))
    .register(staticCall(JAVA_LANG_FLOAT, "isNaN").parameterTypes("float"),
              toValue((arguments, state, factory, method) -> isNaN(arguments, state, FLOAT_NAN)))
    .register(anyOf(
                staticCall("com.google.common.collect.Lists", "newArrayList", "newLinkedList", "newCopyOnWriteArrayList").parameterCount(0),
                staticCall("com.google.common.collect.Sets", "newHashSet", "newLinkedHashSet", "newIdentityHashSet",
                           "newCopyOnWriteArraySet", "newConcurrentHashSet", "newTreeSet").parameterCount(0),
                staticCall("com.google.common.collect.Maps", "newHashMap", "newLinkedHashMap", "newIdentityHashMap",
                           "newConcurrentHashMap", "newTreeMap").parameterCount(0)),
              toValue((arguments, state, factory, method) -> COLLECTION_SIZE.asDfType(intValue(0)).meet(LOCAL_OBJECT)));

  public static CustomMethodHandler find(PsiMethod method) {
    CustomMethodHandler handler = null;
    if (isConstantCall(method)) {
      handler = toValue((arguments, state, factory, m) -> handleConstantCall(arguments, state, m));
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
    if (returnType == null) return DfType.TOP;
    List<Object> args = new ArrayList<>();
    Object qualifierValue = null;
    if (!method.hasModifierProperty(PsiModifier.STATIC)) {
      qualifierValue = getConstantValue(state, arguments.myQualifier);
      if (qualifierValue == null) return DfType.TOP;
    }
    for (DfaValue argument : arguments.myArguments) {
      Object argumentValue = getConstantValue(state, argument);
      if (argumentValue == null) return DfType.TOP;
      if (argumentValue instanceof Long) {
        long longValue = ((Long)argumentValue).longValue();
        if (longValue >= Integer.MIN_VALUE && longValue <= Integer.MAX_VALUE) {
          argumentValue = (int)longValue;
        }
      }
      args.add(argumentValue);
    }
    Method jvmMethod = toJvmMethod(method);
    if (jvmMethod == null) return DfType.TOP;
    Object result;
    try {
      result = jvmMethod.invoke(qualifierValue, args.toArray());
    }
    catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
      return DfType.TOP;
    }
    return constant(result, returnType);
  }

  private static Method toJvmMethod(PsiMethod method) {
    return CachedValuesManager.getCachedValue(method, new CachedValueProvider<>() {
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

  private static @Nullable DfaValue copyOfArray(DfaCallArguments arguments,
                                                DfaValueFactory factory,
                                                PsiMethod method) {
    if (arguments.myArguments.length < 2) return null;
    return factory.getWrapperFactory().createWrapper(typedObject(method.getReturnType(), Nullability.NOT_NULL).meet(LOCAL_OBJECT),
                                                     ARRAY_LENGTH, arguments.myArguments[1]);
  }

  @Contract("null, _, _ -> null; !null, _, _ -> !null")
  private static DfaValue collectionView(@Nullable DfaValue orig,
                                         @NotNull DfaValueFactory factory,
                                         @NotNull PsiMethod method) {
    if (orig == null) return null;
    return factory.getWrapperFactory().createWrapper(typedObject(method.getReturnType(), Nullability.NOT_NULL),
                                                     COLLECTION_SIZE, COLLECTION_SIZE.createValue(factory, orig));
  }

  private static @Nullable DfaValue unmodifiableView(@NotNull DfaCallArguments arguments,
                                                     @NotNull DfaValueFactory factory,
                                                     @NotNull PsiMethod method) {
    if (arguments.myArguments.length != 1) return null;
    return factory.getWrapperFactory().createWrapper(
      typedObject(method.getReturnType(), Nullability.NOT_NULL).meet(Mutability.UNMODIFIABLE_VIEW.asDfType()),
      COLLECTION_SIZE, COLLECTION_SIZE.createValue(factory, arguments.myArguments[0]));
  }

  private static @Nullable DfaValue collectionFactory(DfaCallArguments args,
                                                      DfaMemoryState memState, DfaValueFactory factory,
                                                      PsiMethod method) {
    PsiType type = method.getReturnType();
    if (!(type instanceof PsiClassType)) return null;
    int factor = PsiTypesUtil.classNameEquals(type, JAVA_UTIL_MAP) ? 2 : 1;
    DfaValue size;
    if (method.isVarArgs()) {
      size = ARRAY_LENGTH.createValue(factory, args.myArguments[0]);
    }
    else {
      size = factory.fromDfType(intValue(args.myArguments.length / factor));
    }
    boolean asList = method.getName().equals("asList");
    Mutability mutability = asList ? Mutability.MUTABLE : Mutability.UNMODIFIABLE;
    DfType result = typedObject(type, Nullability.NOT_NULL).meet(mutability.asDfType());
    if (asList) {
      result = result.meet(LOCAL_OBJECT);
    }
    return factory.getWrapperFactory().createWrapper(result, COLLECTION_SIZE, size);
  }

  private static DfType getEmptyCollectionConstant(PsiMethod method) {
    String fieldName = "EMPTY_" + method.getName().substring("empty".length()).toUpperCase(Locale.ROOT);
    PsiClass collectionsClass = method.getContainingClass();
    if (collectionsClass == null) return DfType.TOP;
    PsiField field = collectionsClass.findFieldByName(fieldName, false);
    if (field == null) return DfType.TOP;
    return referenceConstant(field, field.getType());
  }

  private static @Nullable DfaValue substring(DfaCallArguments args, DfaMemoryState state, DfaValueFactory factory, PsiType stringType) {
    if (stringType == null || !stringType.equalsToText(JAVA_LANG_STRING)) return null;
    DfaValue qualifier = args.myQualifier;
    DfaValue[] arguments = args.myArguments;
    if (arguments.length < 1 || arguments.length > 2 || arguments[0] == null) return null;
    DfaValue from = arguments[0];
    DfaValue lenVal = STRING_LENGTH.createValue(factory, qualifier);
    DfaValue to = arguments.length == 1 ? lenVal : arguments[1];
    DfaValue resultLen = factory.getBinOpFactory().create(to, from, state, INT, LongRangeBinOp.MINUS);
    if (resultLen instanceof DfaBinOpValue) {
      resultLen = factory.fromDfType(state.getDfType(resultLen));
    }
    return factory.getWrapperFactory().createWrapper(typedObject(stringType, Nullability.NOT_NULL), STRING_LENGTH, resultLen);
  }

  private static @NotNull DfType mathAbs(DfaValue[] args, DfaMemoryState memState, boolean isLong) {
    DfaValue arg = ArrayUtil.getFirstElement(args);
    if (arg == null) return DfType.TOP;
    DfType type = memState.getDfType(arg);
    LongRangeSet range = isLong ? DfLongType.extractRange(type) : DfIntType.extractRange(type);
    return isLong ? longRange(range.abs(LongRangeType.INT64)) : intRange(range.abs(LongRangeType.INT32));
  }

  private static @NotNull DfType calendarGet(DfaCallArguments arguments, DfaMemoryState state, DfaValueFactory factory) {
    if (arguments.myArguments.length != 1) return DfType.TOP;
    Integer val = state.getDfType(arguments.myArguments[0]).getConstantOfType(Integer.class);
    if (val == null) return DfType.TOP;
    LongRangeSet range = switch (val) {
      case Calendar.DATE -> LongRangeSet.range(1, 31);
      case Calendar.MONTH -> {
        PsiType type = TypeConstraint.fromDfType(state.getDfType(arguments.myQualifier)).getPsiType(factory.getProject());
        if (TypeUtils.typeEquals("java.util.GregorianCalendar", type)) {
          yield LongRangeSet.range(0, 11);
        }
        else {
          // Could be lunar calendar
          yield LongRangeSet.range(0, 12);
        }
      }
      case Calendar.AM_PM -> LongRangeSet.range(0, 1);
      case Calendar.DAY_OF_YEAR -> LongRangeSet.range(1, 366);
      case Calendar.HOUR -> LongRangeSet.range(0, 11);
      case Calendar.HOUR_OF_DAY -> LongRangeSet.range(0, 23);
      case Calendar.MINUTE, Calendar.SECOND -> LongRangeSet.range(0, 59);
      case Calendar.MILLISECOND -> LongRangeSet.range(0, 999);
      default -> null;
    };
    return range == null ? DfType.TOP : intRange(range);
  }

  private static @NotNull DfType skip(DfaValue[] arguments, DfaMemoryState state) {
    if (arguments.length != 1) return DfType.TOP;
    LongRangeSet range = DfLongType.extractRange(state.getDfType(arguments[0]));
    return longRange(LongRangeSet.range(0, Math.max(0, range.max())));
  }

  private static @NotNull DfType numberAsString(DfaCallArguments args, DfaMemoryState state, int bitsPerChar, int maxBits) {
    DfaValue arg = args.myArguments[0];
    if (arg == null) return DfType.TOP;
    LongRangeSet range = DfLongType.extractRange(state.getDfType(arg));
    int usedBits = range.min() >= 0 ? Long.SIZE - Long.numberOfLeadingZeros(range.max()) : maxBits;
    int max = Math.max(1, (usedBits - 1) / bitsPerChar + 1);
    return STRING_LENGTH.asDfType(intRange(LongRangeSet.range(1, max)));
  }

  private static @NotNull DfType enumName(DfaValue qualifier, DfaMemoryState state, PsiType type) {
    DfType dfType = state.getDfType(qualifier);
    PsiEnumConstant value = dfType.getConstantOfType(PsiEnumConstant.class);
    if (value != null) {
      return referenceConstant(value.getName(), type);
    }
    return DfType.TOP;
  }

  private static @NotNull DfType objectGetClass(DfaCallArguments arguments, DfaMemoryState state, DfaValueFactory factory, PsiMethod method) {
    DfaValue qualifier = arguments.myQualifier;
    TypeConstraint fact = TypeConstraint.fromDfType(state.getDfType(qualifier));
    if (fact instanceof TypeConstraint.Exact) {
      PsiType qualifierType = fact.getPsiType(factory.getProject());
      PsiType classType = method.getReturnType();
      if (classType != null && qualifierType != null) {
        return referenceConstant(qualifierType, classType);
      }
    }
    return DfType.TOP;
  }

  private static Object getConstantValue(DfaMemoryState memoryState, DfaValue value) {
    DfType type = DfaUtil.getUnboxedDfType(memoryState, value);
    Object constant = type.getConstantOfType(Object.class);
    if (constant instanceof String && ((String)constant).length() > MAX_STRING_CONSTANT_LENGTH_TO_TRACK) return null;
    return constant;
  }

  private static @NotNull DfType randomNextInt(DfaCallArguments arguments, DfaMemoryState state, DfaValueFactory factory, PsiMethod method) {
    DfaValue[] values = arguments.myArguments;
    if (values == null) return DfType.TOP;
    LongRangeSet fromLowerBound;
    LongRangeSet fromUpperBound;
    if (values.length == 1) {
      fromLowerBound = LongRangeSet.range(0, Integer.MAX_VALUE - 1);
      fromUpperBound = DfIntType.extractRange(state.getDfType(values[0])).fromRelation(RelationType.LT);
    } else if (values.length == 2){
      fromLowerBound = DfIntType.extractRange(state.getDfType(values[0])).fromRelation(RelationType.GE);
      fromUpperBound = DfIntType.extractRange(state.getDfType(values[1])).fromRelation(RelationType.LT);
    } else return DfType.TOP;
    LongRangeSet intersection = fromLowerBound.meet(fromUpperBound);
    return intRangeClamped(intersection);
  }

  private static @NotNull DfType className(DfaMemoryState memState,
                                           DfaValue qualifier,
                                           String name,
                                           PsiType stringType) {
    PsiClassType type = memState.getDfType(qualifier).getConstantOfType(PsiClassType.class);
    if (type != null) {
      PsiClass psiClass = type.resolve();
      if (psiClass != null) {
        String result;
        switch (name) {
          case "getSimpleName" -> result = psiClass instanceof PsiAnonymousClass ? "" : psiClass.getName();
          case "getName" -> {
            if (PsiUtil.isLocalOrAnonymousClass(psiClass)) {
              return DfType.TOP;
            }
            result = ClassUtil.getJVMClassName(psiClass);
          }
          default -> result = psiClass.getQualifiedName();
        }
        return constant(result, stringType);
      }
    }
    return DfType.TOP;
  }

  private static DfType compareInteger(DfaCallArguments args, DfaMemoryState state) {
    DfaValue[] arguments = args.myArguments;
    if (arguments.length != 2) return DfType.TOP;
    RelationType relation = state.getRelation(arguments[0], arguments[1]);
    if (relation == null) {
      LongRangeSet left = DfLongType.extractRange(state.getDfType(arguments[0]));
      LongRangeSet right = DfLongType.extractRange(state.getDfType(arguments[1]));
      if (left.isEmpty() || right.isEmpty()) return DfType.BOTTOM;
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
    return DfType.TOP;
  }

  private static @NotNull DfaValue collectionToArray(DfaCallArguments arguments, DfaMemoryState state, DfaValueFactory factory, PsiMethod method) {
    DfType result = DfType.TOP;
    DfaValue collection = arguments.myQualifier;
    DfaValue collectionSize = COLLECTION_SIZE.createValue(factory, collection);
    LongRangeSet collectionSizeRange = DfIntType.extractRange(state.getDfType(collectionSize));
    DfaValue finalSize = collectionSize;
    if (arguments.myArguments.length == 1) {
      DfaValue array = arguments.myArguments[0];
      DfType arrType = state.getDfType(array);
      TypeConstraint constraint = TypeConstraint.fromDfType(arrType);
      if (constraint.isExact()) {
        result = constraint.asDfType();
      }
      // Array size is max of collection size and argument array size
      DfaValue arrayLength = ARRAY_LENGTH.createValue(factory, array);
      if (!state.areEqual(arrayLength, collectionSize)) {
        LongRangeSet arraySizeRange = DfIntType.extractRange(state.getDfType(arrayLength));
        LongRangeSet biggerArrays = collectionSizeRange.fromRelation(RelationType.GT).meet(arraySizeRange);
        LongRangeSet biggerCollections = arraySizeRange.fromRelation(RelationType.GE).meet(collectionSizeRange);
        if (!biggerArrays.isEmpty()) {
          finalSize = factory.fromDfType(intRange(biggerArrays.join(biggerCollections)));
        }
      }
    }
    else if (arguments.myArguments.length == 0) {
      PsiType type = method.getReturnType();
      if (type instanceof PsiArrayType) {
        // Assume that collection.toArray() always returns Object[] exactly.
        // it may be different (e.g. Arrays.asList().toArray() returned more precise type in older JDK)
        // but it violates the spec.
        result = TypeConstraints.exact(type).asDfType();
      }
    }
    return factory.getWrapperFactory().createWrapper(result.meet(NOT_NULL_OBJECT), ARRAY_LENGTH, finalSize);
  }

  private static @NotNull DfaValue stringToCharArray(DfaCallArguments arguments, DfaMemoryState state, DfaValueFactory factory,
                                                   PsiMethod method) {
    DfaValue string = arguments.myQualifier;
    DfaValue stringLength = STRING_LENGTH.createValue(factory, string);
    return factory.getWrapperFactory().createWrapper(typedObject(PsiType.CHAR.createArrayType(), Nullability.NOT_NULL)
      .meet(LOCAL_OBJECT), ARRAY_LENGTH, stringLength);
  }

  private static @NotNull DfType isNaN(DfaCallArguments arguments, DfaMemoryState state, DfType nan) {
    DfType type = state.getDfType(arguments.myArguments[0]);
    return type.isSuperType(nan) ? type.equals(FLOAT_NAN) || type.equals(DOUBLE_NAN) ? TRUE : BOOLEAN : FALSE;
  }
}
