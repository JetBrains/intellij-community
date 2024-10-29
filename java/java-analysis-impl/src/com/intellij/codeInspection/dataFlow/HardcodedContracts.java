// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInspection.dataFlow.StandardMethodContract.ValueConstraint;
import com.intellij.codeInspection.dataFlow.jvm.SpecialField;
import com.intellij.codeInspection.dataFlow.value.RelationType;
import com.intellij.codeInspection.util.OptionalUtil;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.callMatcher.CallMapper;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.ConstructionUtils;
import com.siyeh.ig.psiutils.MethodUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.codeInspection.dataFlow.ContractReturnValue.*;
import static com.intellij.codeInspection.dataFlow.MethodContract.singleConditionContract;
import static com.intellij.codeInspection.dataFlow.MethodContract.trivialContract;
import static com.intellij.codeInspection.dataFlow.StandardMethodContract.ValueConstraint.*;
import static com.intellij.codeInspection.dataFlow.StandardMethodContract.createConstraintArray;
import static com.intellij.psi.CommonClassNames.*;
import static com.siyeh.ig.callMatcher.CallMatcher.*;

public final class HardcodedContracts {
  private static final List<MethodContract> ARRAY_RANGE_CONTRACTS = List.of(
    singleConditionContract(ContractValue.argument(1), RelationType.GT, ContractValue.argument(0).specialField(SpecialField.ARRAY_LENGTH),
                            fail()),
    singleConditionContract(ContractValue.argument(2), RelationType.GT, ContractValue.argument(0).specialField(SpecialField.ARRAY_LENGTH),
                            fail()),
    singleConditionContract(ContractValue.argument(1), RelationType.GT, ContractValue.argument(2), fail()));

  private static final CallMatcher QUEUE_POLL = anyOf(
    instanceCall(JAVA_UTIL_QUEUE, "poll").parameterCount(0),
    instanceCall("java.util.Deque", "pollFirst", "pollLast").parameterCount(0)
    );
  private static final Set<String> PURE_ARRAY_METHODS = Set.of("binarySearch", "spliterator", "stream", "equals", "deepEquals");
  private static final CallMatcher NO_PARAMETER_LEAK_METHODS =
    anyOf(
      instanceCall(JAVA_UTIL_COLLECTION, "addAll", "removeAll", "retainAll").parameterTypes(JAVA_UTIL_COLLECTION),
      instanceCall(JAVA_UTIL_LIST, "addAll").parameterTypes("int", JAVA_UTIL_COLLECTION),
      instanceCall(JAVA_UTIL_MAP, "putAll").parameterTypes(JAVA_UTIL_MAP));

  /**
   * @param method method to test
   * @return true if given method doesn't spoil the arguments' locality
   */
  public static boolean isKnownNoParameterLeak(@Nullable PsiMethod method) {
    if (method == null) return false;
    if (ConstructionUtils.isCollectionConstructor(method)) {
      PsiClass aClass = method.getContainingClass();
      if (aClass != null) {
        String name = aClass.getQualifiedName();
        return name != null && name.startsWith("java.util.") &&
               (InheritanceUtil.isInheritor(aClass, JAVA_UTIL_COLLECTION) ||
                InheritanceUtil.isInheritor(aClass, JAVA_UTIL_MAP));
      }
    }
    return NO_PARAMETER_LEAK_METHODS.methodMatches(method);
  }

  private static final CallMatcher NO_QUALIFIER_LEAK_MATCHERS =
    //not fully clear, but they don't affect on CONSUMED_STREAM and other tracked parameters
    ConsumedStreamUtils.getAllNonLeakStreamMatchers();

  /**
   * @param method   method to test
   * @return true if given method doesn't spoil its qualifier
   */
  @Contract(value = "null -> false", pure = true)
  public static boolean isKnownNoQualifierLeak(@Nullable PsiMethod method) {
    return NO_QUALIFIER_LEAK_MATCHERS.methodMatches(method);
  }

  @FunctionalInterface
  interface ContractProvider {
    List<MethodContract> getContracts(PsiMethodCallExpression call, int paramCount);

    static ContractProvider of(MethodContract contract) {
      return (call, paramCount) -> Collections.singletonList(contract);
    }

    static ContractProvider of(MethodContract... contracts) {
      return (call, paramCount) -> Arrays.asList(contracts);
    }
  }

  private static final CallMapper<ContractProvider> HARDCODED_CONTRACTS = new CallMapper<ContractProvider>()
    .register(anyOf(staticCall("com.google.common.base.Preconditions", "checkNotNull"),
                    staticCall(JAVA_UTIL_OBJECTS, "requireNonNull")),
              (call, cnt) -> cnt > 0 ? failIfNull(0, cnt, true) : null)
    .register(staticCall("com.google.common.base.Preconditions", "checkArgument", "checkState"),
              (call, cnt) -> {
                if (cnt == 0) return null;
                ValueConstraint[] constraints = createConstraintArray(cnt);
                constraints[0] = FALSE_VALUE;
                return Collections.singletonList(new StandardMethodContract(constraints, fail()));
              })
    .register(instanceCall(JAVA_LANG_STRING, "charAt", "codePointAt").parameterCount(1),
              ContractProvider.of(specialFieldRangeContract(0, RelationType.LT, SpecialField.STRING_LENGTH)))
    .register(anyOf(instanceCall(JAVA_LANG_STRING, "substring", "subSequence").parameterCount(2),
                    instanceCall(JAVA_LANG_STRING, "substring").parameterCount(1)),
              (call, cnt) -> getSubstringContracts(cnt == 2))
    .register(instanceCall(JAVA_LANG_STRING, "isEmpty").parameterCount(0),
              ContractProvider.of(SpecialField.STRING_LENGTH.getEmptyContracts()))
    .register(instanceCall(JAVA_LANG_STRING, "isBlank").parameterCount(0),
              ContractProvider.of(singleConditionContract(
                ContractValue.qualifier().specialField(SpecialField.STRING_LENGTH), RelationType.EQ, ContractValue.zero(), returnTrue())))
    .register(anyOf(instanceCall(JAVA_UTIL_COLLECTION, "isEmpty").parameterCount(0),
                    instanceCall(JAVA_UTIL_MAP, "isEmpty").parameterCount(0)),
              ContractProvider.of(SpecialField.COLLECTION_SIZE.getEmptyContracts()))
    .register(instanceCall(JAVA_LANG_STRING, "equalsIgnoreCase").parameterCount(1),
              ContractProvider.of(SpecialField.STRING_LENGTH.getEqualsContracts()))
    .register(anyOf(instanceCall(JAVA_UTIL_SET, "equals").parameterTypes(JAVA_LANG_OBJECT),
                    instanceCall(JAVA_UTIL_LIST, "equals").parameterTypes(JAVA_LANG_OBJECT),
                    instanceCall(JAVA_UTIL_MAP, "equals").parameterTypes(JAVA_LANG_OBJECT)),
              ContractProvider.of(SpecialField.COLLECTION_SIZE.getEqualsContracts()))
    .register(anyOf(instanceCall(JAVA_UTIL_COLLECTION, "contains").parameterCount(1),
                    instanceCall(JAVA_UTIL_MAP, "containsKey", "containsValue").parameterCount(1)),
              ContractProvider.of(singleConditionContract(
                ContractValue.qualifier().specialField(SpecialField.COLLECTION_SIZE), RelationType.EQ, ContractValue.zero(),
                returnFalse())))
    .register(anyOf(instanceCall(JAVA_UTIL_SET, "add").parameterCount(1)),
              ContractProvider.of(singleConditionContract(
                ContractValue.qualifier().specialField(SpecialField.COLLECTION_SIZE), RelationType.EQ, ContractValue.zero(),
                returnTrue())))
    .register(instanceCall(JAVA_UTIL_LIST, "get", "remove").parameterTypes("int"),
              ContractProvider.of(specialFieldRangeContract(0, RelationType.LT, SpecialField.COLLECTION_SIZE)))
    .register(anyOf(
      instanceCall(JAVA_UTIL_SORTED_SET, "first", "last").parameterCount(0),
      instanceCall("java.util.Deque", "getFirst", "getLast").parameterCount(0),
      instanceCall(JAVA_UTIL_QUEUE, "element").parameterCount(0)),
              ContractProvider.of(singleConditionContract(
                ContractValue.qualifier().specialField(SpecialField.COLLECTION_SIZE), RelationType.EQ,
                ContractValue.zero(), fail())))
    // All these methods take array as 1st parameter, from index as 2nd and to index as 3rd
    // thus ARRAY_RANGE_CONTRACTS are applicable to them
    .register(staticCall(JAVA_UTIL_ARRAYS, "fill", "parallelPrefix", "parallelSort", "sort", "spliterator", "stream"),
              (call, cnt) -> cnt >= 3 ? ARRAY_RANGE_CONTRACTS : null)
    .register(staticCall(JAVA_UTIL_ARRAYS, "binarySearch"),
              (call, cnt) -> cnt >= 4 ? ARRAY_RANGE_CONTRACTS : null)
    .register(staticCall("org.mockito.ArgumentMatchers", "argThat", "assertArg").parameterCount(1),
              ContractProvider.of(StandardMethodContract.fromText("_->_")))
    .register(anyOf(
      instanceCall(JAVA_UTIL_QUEUE, "peek", "poll").parameterCount(0),
      instanceCall("java.util.Deque", "peekFirst", "peekLast", "pollFirst", "pollLast").parameterCount(0)),
              (call, paramCount) -> Arrays.asList(singleConditionContract(
                ContractValue.qualifier().specialField(SpecialField.COLLECTION_SIZE), RelationType.EQ,
                ContractValue.zero(), returnNull()), trivialContract(returnAny())))
    .register(anyOf(staticCall(JAVA_LANG_MATH, "max").parameterTypes("int", "int"),
                    staticCall(JAVA_LANG_MATH, "max").parameterTypes("long", "long"),
                    staticCall(JAVA_LANG_INTEGER, "max").parameterTypes("int", "int"),
                    staticCall(JAVA_LANG_LONG, "max").parameterTypes("long", "long")),
              (call, paramCount) -> mathMinMax(true))
    .register(anyOf(staticCall(JAVA_LANG_MATH, "min").parameterTypes("int", "int"),
                    staticCall(JAVA_LANG_MATH, "min").parameterTypes("long", "long"),
                    staticCall(JAVA_LANG_INTEGER, "min").parameterTypes("int", "int"),
                    staticCall(JAVA_LANG_LONG, "min").parameterTypes("long", "long")),
              (call, paramCount) -> mathMinMax(false))
    .register(instanceCall(JAVA_LANG_STRING, "startsWith", "endsWith", "contains"),
              ContractProvider.of(
                singleConditionContract(
                  ContractValue.argument(0).specialField(SpecialField.STRING_LENGTH), RelationType.EQ,
                  ContractValue.zero(), returnTrue()),
                singleConditionContract(
                  ContractValue.argument(0), RelationType.EQ,
                  ContractValue.qualifier(), returnTrue()),
                singleConditionContract(
                  ContractValue.qualifier().specialField(SpecialField.STRING_LENGTH), RelationType.LT,
                  ContractValue.argument(0).specialField(SpecialField.STRING_LENGTH), returnFalse())))
    .register(instanceCall(JAVA_LANG_OBJECT, "equals").parameterTypes(JAVA_LANG_OBJECT),
              (call, paramCount) -> equalsContracts(call))
    .register(anyOf(
      staticCall(JAVA_UTIL_OBJECTS, "equals").parameterCount(2),
      staticCall(JAVA_UTIL_ARRAYS, "equals", "deepEquals").parameterCount(2),
      staticCall("com.google.common.base.Objects", "equal").parameterCount(2)),
              ContractProvider.of(
                singleConditionContract(ContractValue.argument(0), RelationType.EQ, ContractValue.argument(1),
                                        returnTrue()),
                StandardMethodContract.fromText("null,!null->false"),
                StandardMethodContract.fromText("!null,null->false")
              ))
    .register(enumValues(), ContractProvider.of(StandardMethodContract.fromText("->new")))
    .register(staticCall("java.lang.System", "arraycopy"), expression -> getArraycopyContract())
    .register(anyOf(
                instanceCall(JAVA_TIME_LOCAL_DATE, "isAfter"),
                instanceCall(JAVA_TIME_LOCAL_TIME, "isAfter"),
                instanceCall(JAVA_TIME_OFFSET_TIME, "isAfter"),
                instanceCall(JAVA_TIME_OFFSET_DATE_TIME, "isAfter"),
                instanceCall(JAVA_TIME_ZONED_DATE_TIME, "isAfter"),
                instanceCall(JAVA_TIME_LOCAL_DATE_TIME, "isAfter"),
                instanceCall("java.util.Date", "after"),
                instanceCall("java.time.Year", "isAfter"),
                instanceCall("java.time.YearMonth", "isAfter")),
              ContractProvider.of(
                singleConditionContract(ContractValue.qualifier(), RelationType.GT, ContractValue.argument(0), returnBoolean(true)),
                trivialContract(returnBoolean(false))))
    .register(anyOf(
                instanceCall(JAVA_TIME_LOCAL_DATE, "isBefore"),
                instanceCall(JAVA_TIME_LOCAL_TIME, "isBefore"),
                instanceCall(JAVA_TIME_OFFSET_TIME, "isBefore"),
                instanceCall(JAVA_TIME_OFFSET_DATE_TIME, "isBefore"),
                instanceCall(JAVA_TIME_ZONED_DATE_TIME, "isBefore"),
                instanceCall(JAVA_TIME_LOCAL_DATE_TIME, "isBefore"),
                instanceCall("java.util.Date", "before"),
                instanceCall("java.time.Year", "isBefore"),
                instanceCall("java.time.YearMonth", "isBefore")),
              ContractProvider.of(
                singleConditionContract(ContractValue.qualifier(), RelationType.LT, ContractValue.argument(0), returnBoolean(true)),
                trivialContract(returnBoolean(false))))
    //for propagation CONSUMED_STREAM
    .register(ConsumedStreamUtils.getSkipStreamMatchers(), ContractProvider.of(trivialContract(returnThis())))
    .register(staticCall(JAVA_LANG_CHARACTER, "isSurrogate").parameterCount(1),
              ContractProvider.of(
                singleConditionContract(ContractValue.argument(0), RelationType.LT, ContractValue.constant(Character.MIN_SURROGATE,
                                                                                                           PsiTypes.charType()), returnFalse()),
                singleConditionContract(ContractValue.argument(0), RelationType.GT, ContractValue.constant(Character.MAX_SURROGATE,
                                                                                                           PsiTypes.charType()), returnFalse()),
                trivialContract(returnTrue())))
    .register(staticCall(JAVA_LANG_CHARACTER, "isHighSurrogate").parameterCount(1),
              ContractProvider.of(
                singleConditionContract(ContractValue.argument(0), RelationType.LT, ContractValue.constant(Character.MIN_HIGH_SURROGATE,
                                                                                                           PsiTypes.charType()), returnFalse()),
                singleConditionContract(ContractValue.argument(0), RelationType.GT, ContractValue.constant(Character.MAX_HIGH_SURROGATE,
                                                                                                           PsiTypes.charType()), returnFalse()),
                trivialContract(returnTrue())))
    .register(staticCall(JAVA_LANG_CHARACTER, "isLowSurrogate").parameterCount(1),
              ContractProvider.of(
                singleConditionContract(ContractValue.argument(0), RelationType.LT, ContractValue.constant(Character.MIN_LOW_SURROGATE,
                                                                                                           PsiTypes.charType()), returnFalse()),
                singleConditionContract(ContractValue.argument(0), RelationType.GT, ContractValue.constant(Character.MAX_LOW_SURROGATE,
                                                                                                           PsiTypes.charType()), returnFalse()),
                trivialContract(returnTrue())))
    .register(staticCall(JAVA_LANG_CHARACTER, "isSupplementaryCodePoint").parameterCount(1),
              ContractProvider.of(
                singleConditionContract(ContractValue.argument(0), RelationType.LT, ContractValue.constant(Character.MIN_SUPPLEMENTARY_CODE_POINT,
                                                                                                           PsiTypes.intType()), returnFalse()),
                singleConditionContract(ContractValue.argument(0), RelationType.GT, ContractValue.constant(Character.MAX_CODE_POINT,
                                                                                                           PsiTypes.intType()), returnFalse()),
                trivialContract(returnTrue())))
    .register(staticCall(JAVA_LANG_CHARACTER, "isValidCodePoint").parameterCount(1),
              ContractProvider.of(
                singleConditionContract(ContractValue.argument(0), RelationType.LT, ContractValue.constant(Character.MIN_CODE_POINT,
                                                                                                           PsiTypes.intType()), returnFalse()),
                singleConditionContract(ContractValue.argument(0), RelationType.GT, ContractValue.constant(Character.MAX_CODE_POINT,
                                                                                                           PsiTypes.intType()), returnFalse()),
                trivialContract(returnTrue())))
    .register(staticCall(JAVA_LANG_CHARACTER, "isBmpCodePoint").parameterCount(1),
              ContractProvider.of(
                singleConditionContract(ContractValue.argument(0), RelationType.LT, ContractValue.constant(Character.MIN_CODE_POINT,
                                                                                                           PsiTypes.intType()), returnFalse()),
                singleConditionContract(ContractValue.argument(0), RelationType.GE, ContractValue.constant(Character.MIN_SUPPLEMENTARY_CODE_POINT,
                                                                                                           PsiTypes.intType()), returnFalse()),
                trivialContract(returnTrue())))
    .register(staticCall("org.springframework.util.CollectionUtils", "isEmpty").parameterCount(1),
              ContractProvider.of(
                singleConditionContract(ContractValue.argument(0), RelationType.EQ, ContractValue.nullValue(), returnTrue()),
                singleConditionContract(ContractValue.argument(0).specialField(SpecialField.COLLECTION_SIZE), RelationType.EQ, ContractValue.zero(), returnTrue()),
                trivialContract(returnFalse())
              ))
    .register(instanceCall("java.util.concurrent.TimeUnit", "convert").parameterCount(2),
              ContractProvider.of(
                singleConditionContract(ContractValue.qualifier(), RelationType.EQ, ContractValue.argument(1), returnParameter(0))
              ))
    ;

  private static @NotNull ContractProvider getArraycopyContract() {
    ContractValue src = ContractValue.argument(0);
    ContractValue srcPos = ContractValue.argument(1);
    ContractValue dest = ContractValue.argument(2);
    ContractValue destPos = ContractValue.argument(3);
    ContractValue length = ContractValue.argument(4);
    ContractValue srcLength = src.specialField(SpecialField.ARRAY_LENGTH);
    ContractValue dstLength = dest.specialField(SpecialField.ARRAY_LENGTH);
    return ContractProvider.of(
      singleConditionContract(srcPos, RelationType.GT, srcLength, fail()),
      singleConditionContract(destPos, RelationType.GT, dstLength, fail()),
      singleConditionContract(srcPos, RelationType.LT, ContractValue.zero(), fail()),
      singleConditionContract(destPos, RelationType.LT, ContractValue.zero(), fail()),
      singleConditionContract(length, RelationType.LT, ContractValue.zero(), fail()),
      singleConditionContract(length, RelationType.GT, srcLength, fail()),
      singleConditionContract(length, RelationType.GT, dstLength, fail())
    );
  }

  public static List<MethodContract> getHardcodedContracts(@NotNull PsiMethod method, @Nullable PsiMethodCallExpression call) {
    PsiClass owner = method.getContainingClass();
    if (owner == null) {
      return Collections.emptyList();
    }
    PsiFile file = owner.getContainingFile();
    if (file != null && InjectedLanguageManager.getInstance(owner.getProject()).isInjectedFragment(file)) {
      return Collections.emptyList();
    }

    final int paramCount = method.getParameterList().getParametersCount();
    String className = owner.getQualifiedName();
    if (className == null) return Collections.emptyList();

    if (method.isConstructor()) {
      if (className.equals("java.util.concurrent.ArrayBlockingQueue") && paramCount == 3) {
        return List.of(singleConditionContract(ContractValue.argument(0), RelationType.LT,
                                               ContractValue.argument(2).specialField(SpecialField.COLLECTION_SIZE), fail()),
                       singleConditionContract(ContractValue.argument(0), RelationType.LE,
                                               ContractValue.constant(0, PsiTypes.intType()), fail()));
      }
    }

    ContractProvider provider = HARDCODED_CONTRACTS.mapFirst(method);
    if (provider != null) {
      List<MethodContract> contracts = provider.getContracts(call, paramCount);
      if (contracts != null) {
        return contracts;
      }
    }
    String methodName = method.getName();

    if ("org.apache.commons.lang.Validate".equals(className) ||
        "org.apache.commons.lang3.Validate".equals(className) ||
        "org.springframework.util.Assert".equals(className)) {
      if (("isTrue".equals(methodName) || "state".equals(methodName)) && paramCount > 0) {
        ValueConstraint[] constraints = createConstraintArray(paramCount);
        constraints[0] = FALSE_VALUE;
        return Collections.singletonList(new StandardMethodContract(constraints, fail()));
      }
      if ("notNull".equals(methodName) && paramCount > 0) {
        ValueConstraint[] constraints = createConstraintArray(paramCount);
        constraints[0] = NULL_VALUE;
        StandardMethodContract contract = new StandardMethodContract(constraints, fail());
        if (PsiTypes.voidType().equals(method.getReturnType())) {
          return Collections.singletonList(contract);
        }
        else {
          return Arrays.asList(contract, new StandardMethodContract(createConstraintArray(paramCount), returnParameter(0)));
        }
      }
    }
    else if (isJunit(className) || isTestng(className) ||
             className.equals("org.hamcrest.MatcherAssert") ||
             className.equals("org.hamcrest.junit.MatcherAssert")) {
      return handleTestFrameworks(method, paramCount, className, methodName, call);
    }
    else if (TypeUtils.isOptional(owner)) {
      if (OptionalUtil.OPTIONAL_GET.methodMatches(method) || "orElseThrow".equals(methodName)) {
        return Collections.singletonList(optionalAbsentContract(fail()));
      }
      else if ("isPresent".equals(methodName) && paramCount == 0) {
        return Arrays.asList(optionalAbsentContract(returnFalse()), trivialContract(returnTrue()));
      }
      else if ("isEmpty".equals(methodName) && paramCount == 0) {
        return Arrays.asList(optionalAbsentContract(returnTrue()), trivialContract(returnFalse()));
      }
    }
    else if (MethodUtils.isEquals(method)) {
      return Collections.singletonList(new StandardMethodContract(new ValueConstraint[]{NULL_VALUE}, returnFalse()));
    }

    return Collections.emptyList();
  }

  private static @NotNull List<MethodContract> getSubstringContracts(boolean endLimited) {
    List<MethodContract> contracts = new ArrayList<>(3);
    contracts.add(specialFieldRangeContract(0, RelationType.LE, SpecialField.STRING_LENGTH));
    if (endLimited) {
      contracts.add(specialFieldRangeContract(1, RelationType.LE, SpecialField.STRING_LENGTH));
      contracts.add(singleConditionContract(ContractValue.argument(0), RelationType.LE.getNegated(), ContractValue.argument(1), fail()));
    }
    return contracts;
  }

  static MethodContract optionalAbsentContract(ContractReturnValue returnValue) {
    return singleConditionContract(ContractValue.qualifier().specialField(SpecialField.OPTIONAL_VALUE), RelationType.EQ,
                                   ContractValue.nullValue(), returnValue);
  }

  static MethodContract specialFieldRangeContract(int index, RelationType type, SpecialField specialField) {
    return singleConditionContract(ContractValue.argument(index), type.getNegated(), ContractValue.qualifier().specialField(specialField),
                                   fail());
  }

  static List<MethodContract> mathMinMax(boolean isMax) {
    return Arrays.asList(singleConditionContract(
      ContractValue.argument(0), isMax ? RelationType.GT : RelationType.LT, ContractValue.argument(1), returnParameter(0)),
                         trivialContract(returnParameter(1)));
  }

  private static List<MethodContract> equalsContracts(PsiMethodCallExpression call) {
    PsiExpression qualifier = call == null ? null : call.getMethodExpression().getQualifierExpression();
    if (qualifier != null) {
      PsiType type = qualifier.getType();
      if (type != null && (knownAsEqualByReference(type) || TypeConstraints.exact(type).isComparedByEquals())) {
        return Arrays.asList(
          singleConditionContract(ContractValue.qualifier(), RelationType.EQ, ContractValue.argument(0), returnTrue()),
          trivialContract(returnFalse())
        );
      }
    }
    return Arrays.asList(new StandardMethodContract(new StandardMethodContract.ValueConstraint[]{NULL_VALUE}, returnFalse()),
                         singleConditionContract(ContractValue.qualifier(), RelationType.EQ,
                                                 ContractValue.argument(0), returnTrue()));
  }

  private static boolean knownAsEqualByReference(PsiType type) {
    if (type instanceof PsiArrayType) return true;
    PsiClass psiClass = PsiUtil.resolveClassInClassTypeOnly(type);
    return psiClass != null && (psiClass.isEnum() || JAVA_LANG_CLASS.equals(psiClass.getQualifiedName()));
  }

  private static boolean isJunit(String className) {
    return className.startsWith("junit.framework.") || className.startsWith("org.junit.")
           || className.equals("org.testng.AssertJUnit");
  }

  private static boolean isJunit5(String className) {
    return className.startsWith("org.junit.jupiter.");
  }

  private static boolean isTestng(String className) {
    return className.startsWith("org.testng.") && !className.equals("org.testng.AssertJUnit");
  }

  private static List<MethodContract> handleTestFrameworks(PsiMethod method,
                                                           int paramCount,
                                                           String className,
                                                           String methodName,
                                                           @Nullable PsiMethodCallExpression call) {
    if (call != null && ("assertThat".equals(methodName) || "assumeThat".equals(methodName) || "that".equals(methodName))) {
      return handleAssertThat(paramCount, call);
    }

    if (!isJunit(className) && !isTestng(className)) {
      return Collections.emptyList();
    }

    boolean testng = isTestng(className);
    if ("fail".equals(methodName)) {
      return Collections.singletonList(StandardMethodContract.trivialContract(paramCount, fail()));
    }

    if (paramCount == 0) return Collections.emptyList();

    int checkedParam = testng || isJunit5(className) ? 0 : paramCount - 1;
    PsiType type = Objects.requireNonNull(method.getParameterList().getParameter(checkedParam)).getType();
    ValueConstraint[] constraints = createConstraintArray(paramCount);
    if (("assertTrue".equals(methodName) || "assumeTrue".equals(methodName)) && PsiTypes.booleanType().equals(type)) {
      constraints[checkedParam] = FALSE_VALUE;
      return Collections.singletonList(new StandardMethodContract(constraints, fail()));
    }
    if (("assertFalse".equals(methodName) || "assumeFalse".equals(methodName)) && PsiTypes.booleanType().equals(type)) {
      constraints[checkedParam] = TRUE_VALUE;
      return Collections.singletonList(new StandardMethodContract(constraints, fail()));
    }
    if ("assertNull".equals(methodName) && TypeUtils.isJavaLangObject(type)) {
      constraints[checkedParam] = NOT_NULL_VALUE;
      return Collections.singletonList(new StandardMethodContract(constraints, fail()));
    }
    if ("assertNotNull".equals(methodName) && TypeUtils.isJavaLangObject(type)) {
      return failIfNull(checkedParam, paramCount, false);
    }
    return Collections.emptyList();
  }

  private static @Nullable ValueConstraint constraintFromMatcher(PsiExpression expr, boolean negate) {
    if (!(expr instanceof PsiMethodCallExpression call)) return null;
    String calledName = call.getMethodExpression().getReferenceName();
    PsiExpression[] args = call.getArgumentList().getExpressions();
    if (calledName == null) return null;
    return switch (calledName) {
      case "array", "arrayWithSize", "arrayContaining", "arrayContainingInAnyOrder", "contains", "containsInAnyOrder", "containsString",
        "endsWith", "startsWith", "stringContainsInOrder", "empty", "emptyArray", "emptyCollectionOf", "emptyIterable", "emptyIterableOf",
        "hasEntry", "hasItem", "hasItems", "hasKey", "hasProperty", "hasSize", "hasToString", "hasValue", "hasXPath" ->
        negate ? null : NULL_VALUE;
      case "notNullValue" -> negate ? NOT_NULL_VALUE : NULL_VALUE;
      case "nullValue" -> negate ? NULL_VALUE : NOT_NULL_VALUE;
      case "equalTo" -> {
        if (args.length == 1) {
          yield constraintFromLiteral(args[0], negate);
        }
        yield null;
      }
      case "not" -> {
        if (args.length == 1) {
          yield constraintFromMatcher(args[0], !negate);
        }
        yield null;
      }
      case "is" -> {
        if (args.length == 1) {
          ValueConstraint fromMatcher = constraintFromMatcher(args[0], negate);
          yield fromMatcher == null ? constraintFromLiteral(args[0], negate) : fromMatcher;
        }
        yield null;
      }
      default -> null;
    };
  }

  private static @Nullable ValueConstraint constraintFromLiteral(PsiExpression arg, boolean negate) {
    arg = PsiUtil.skipParenthesizedExprDown(arg);
    if (!(arg instanceof PsiLiteralExpression)) return null;
    Object value = ((PsiLiteralExpression)arg).getValue();
    if (value == null) return negate ? NULL_VALUE : NOT_NULL_VALUE;
    if (Boolean.TRUE.equals(value)) return negate ? TRUE_VALUE : FALSE_VALUE;
    if (Boolean.FALSE.equals(value)) return negate ? FALSE_VALUE : TRUE_VALUE;
    return null;
  }

  private static @NotNull List<MethodContract> handleAssertThat(int paramCount, @NotNull PsiMethodCallExpression call) {
    PsiExpression[] args = call.getArgumentList().getExpressions();
    if (args.length == paramCount) {
      for (int i = 1; i < args.length; i++) {
        ValueConstraint constraint = constraintFromMatcher(args[i], false);
        if (constraint != null) {
          ValueConstraint[] constraints = createConstraintArray(paramCount);
          constraints[i - 1] = constraint;
          return Collections.singletonList(new StandardMethodContract(constraints, fail()));
        }
      }
    }
    return Collections.emptyList();
  }

  private static @NotNull List<MethodContract> failIfNull(int argIndex, int argCount, boolean returnArg) {
    ValueConstraint[] constraints = createConstraintArray(argCount);
    constraints[argIndex] = NULL_VALUE;
    StandardMethodContract failContract = new StandardMethodContract(constraints, fail());
    if (returnArg) {
      return Arrays.asList(failContract, StandardMethodContract.trivialContract(argCount, returnParameter(argIndex)));
    }
    return Collections.singletonList(failContract);
  }

  /**
   * Returns the mutation signature for the methods that have hardcoded contracts
   *
   * @param method method that has hardcoded contracts (that is, {@link #getHardcodedContracts(PsiMethod, PsiMethodCallExpression)}
   *               returned non-empty list for this method)
   * @return a mutation signature for the given method. Result is unspecified if method has no hardcoded contract.
   */
  public static MutationSignature getHardcodedMutation(PsiMethod method) {
    PsiClass aClass = method.getContainingClass();
    if (aClass == null) return MutationSignature.unknown();
    String className = aClass.getQualifiedName();
    if (className == null) return MutationSignature.unknown();
    String name = method.getName();

    if ("java.util.Objects".equals(className) && "requireNonNull".equals(name)) {
      PsiParameter[] parameters = method.getParameterList().getParameters();
      if (parameters.length == 2 && parameters[1].getType().getCanonicalText().contains("Supplier")) {
        return MutationSignature.unknown();
      }
    }

    if ("remove".equals(name) || "add".equals(name)) {
      return MutationSignature.pure().alsoMutatesThis();
    }

    if ("java.lang.System".equals(className)) {
      return MutationSignature.unknown();
    }
    if (JAVA_UTIL_ARRAYS.equals(className)) {
      return PURE_ARRAY_METHODS.contains(name) ? MutationSignature.pure() :
             // else: fill, parallelPrefix, parallelSort, sort
             MutationSignature.pure().alsoMutatesArg(0);
    }
    if (QUEUE_POLL.methodMatches(method)) {
      return MutationSignature.pure().alsoMutatesThis();
    }
    return MutationSignature.pure();
  }

  public static boolean hasHardcodedContracts(@Nullable PsiElement element) {
    if (element instanceof PsiMethod) {
      return !getHardcodedContracts((PsiMethod)element, null).isEmpty();
    }

    if (element instanceof PsiParameter) {
      PsiElement parent = element.getParent();
      return parent != null && hasHardcodedContracts(parent.getParent());
    }

    return false;
  }
}
