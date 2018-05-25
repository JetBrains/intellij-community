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

import com.intellij.codeInspection.dataFlow.StandardMethodContract.ValueConstraint;
import com.intellij.codeInspection.dataFlow.value.DfaRelationValue.RelationType;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.callMatcher.CallMapper;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.MethodUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import static com.intellij.codeInspection.dataFlow.ContractReturnValue.*;
import static com.intellij.codeInspection.dataFlow.MethodContract.trivialContract;
import static com.intellij.codeInspection.dataFlow.StandardMethodContract.ValueConstraint.*;
import static com.intellij.codeInspection.dataFlow.StandardMethodContract.createConstraintArray;
import static com.intellij.psi.CommonClassNames.*;
import static com.siyeh.ig.callMatcher.CallMatcher.*;

/**
 * @author peter
 */
public class HardcodedContracts {
  private static final List<MethodContract> ARRAY_RANGE_CONTRACTS = ContainerUtil.immutableList(
    nonnegativeArgumentContract(1),
    nonnegativeArgumentContract(2),
    MethodContract.singleConditionContract(ContractValue.argument(1), RelationType.GT,
                                           ContractValue.argument(0).specialField(SpecialField.ARRAY_LENGTH), fail()),
    MethodContract.singleConditionContract(ContractValue.argument(2), RelationType.GT,
                                           ContractValue.argument(0).specialField(SpecialField.ARRAY_LENGTH), fail()),
    MethodContract.singleConditionContract(ContractValue.argument(1), RelationType.GT, ContractValue.argument(2), fail())
  );

  private static final CallMatcher QUEUE_POLL = instanceCall("java.util.Queue", "poll").parameterCount(0);

  @FunctionalInterface
  interface ContractProvider {
    List<MethodContract> getContracts(PsiMethodCallExpression call, int paramCount);

    static ContractProvider single(Supplier<? extends MethodContract> supplier) {
      return (call, paramCount) -> Collections.singletonList(supplier.get());
    }

    static ContractProvider list(Supplier<? extends List<MethodContract>> supplier) {
      return (call, paramCount) -> supplier.get();
    }
  }

  private static final CallMapper<ContractProvider> HARDCODED_CONTRACTS = new CallMapper<ContractProvider>()
    .register(staticCall("java.lang.System", "exit").parameterCount(1),
              ContractProvider.single(() -> new StandardMethodContract(new ValueConstraint[0], fail())))
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
              ContractProvider.list(() -> Arrays.asList(nonnegativeArgumentContract(0),
                                                        specialFieldRangeContract(0, RelationType.LT, SpecialField.STRING_LENGTH))))
    .register(anyOf(instanceCall(JAVA_LANG_STRING, "substring", "subSequence").parameterCount(2),
                    instanceCall(JAVA_LANG_STRING, "substring").parameterCount(1)),
              (call, cnt) -> getSubstringContracts(cnt == 2))
    .register(instanceCall(JAVA_LANG_STRING, "isEmpty").parameterCount(0),
              ContractProvider.list(SpecialField.STRING_LENGTH::getEmptyContracts))
    .register(instanceCall(JAVA_UTIL_COLLECTION, "isEmpty").parameterCount(0),
              ContractProvider.list(SpecialField.COLLECTION_SIZE::getEmptyContracts))
    .register(instanceCall(JAVA_UTIL_MAP, "isEmpty").parameterCount(0),
              ContractProvider.list(SpecialField.MAP_SIZE::getEmptyContracts))
    .register(instanceCall(JAVA_LANG_STRING, "equals", "equalsIgnoreCase").parameterCount(1),
              ContractProvider.list(SpecialField.STRING_LENGTH::getEqualsContracts))
    .register(anyOf(instanceCall(JAVA_UTIL_SET, "equals").parameterTypes(JAVA_LANG_OBJECT),
                    instanceCall(JAVA_UTIL_LIST, "equals").parameterTypes(JAVA_LANG_OBJECT)),
              ContractProvider.list(SpecialField.COLLECTION_SIZE::getEqualsContracts))
    .register(instanceCall(JAVA_UTIL_MAP, "equals").parameterTypes(JAVA_LANG_OBJECT),
              ContractProvider.list(SpecialField.MAP_SIZE::getEqualsContracts))
    .register(instanceCall(JAVA_UTIL_COLLECTION, "contains").parameterCount(1),
              ContractProvider.single(() -> MethodContract.singleConditionContract(
                ContractValue.qualifier().specialField(SpecialField.COLLECTION_SIZE), RelationType.EQ, ContractValue.zero(),
                returnFalse())))
    .register(instanceCall(JAVA_UTIL_MAP, "containsKey", "containsValue").parameterCount(1),
              ContractProvider.single(() -> MethodContract.singleConditionContract(
                ContractValue.qualifier().specialField(SpecialField.MAP_SIZE), RelationType.EQ, ContractValue.zero(), returnFalse())))
    .register(instanceCall(JAVA_UTIL_LIST, "get").parameterTypes("int"),
              ContractProvider.list(() -> Arrays.asList(nonnegativeArgumentContract(0),
                                                        specialFieldRangeContract(0, RelationType.LT, SpecialField.COLLECTION_SIZE))))
    .register(instanceCall("java.util.SortedSet", "first", "last").parameterCount(0),
              ContractProvider.single(() -> MethodContract.singleConditionContract(
                ContractValue.qualifier().specialField(SpecialField.COLLECTION_SIZE), RelationType.EQ,
                ContractValue.zero(), fail())))
    // All these methods take array as 1st parameter, from index as 2nd and to index as 3rd
    // thus ARRAY_RANGE_CONTRACTS are applicable to them
    .register(staticCall(JAVA_UTIL_ARRAYS, "binarySearch", "fill", "parallelPrefix", "parallelSort", "sort", "spliterator", "stream"),
              (call, cnt) -> cnt >= 3 ? ARRAY_RANGE_CONTRACTS : null)
    .register(staticCall("org.mockito.ArgumentMatchers", "argThat").parameterCount(1),
              ContractProvider.single(() -> new StandardMethodContract(new ValueConstraint[]{ANY_VALUE}, returnAny())))
    .register(instanceCall("java.util.Queue", "peek", "poll").parameterCount(0),
              (call, paramCount) -> Arrays.asList(MethodContract.singleConditionContract(
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
              (call, paramCount) -> mathMinMax(false));

  public static List<MethodContract> getHardcodedContracts(@NotNull PsiMethod method, @Nullable PsiMethodCallExpression call) {
    PsiClass owner = method.getContainingClass();
    if (owner == null ||
        InjectedLanguageManager.getInstance(owner.getProject()).isInjectedFragment(owner.getContainingFile())) {
      return Collections.emptyList();
    }

    final int paramCount = method.getParameterList().getParametersCount();
    String className = owner.getQualifiedName();
    if (className == null) return Collections.emptyList();

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
        return Collections.singletonList(new StandardMethodContract(constraints, fail()));
      }
    }
    else if (isJunit(className) || isTestng(className) ||
             className.startsWith("com.google.common.truth.") ||
             className.startsWith("org.assertj.core.api.") ||
             className.equals("org.hamcrest.MatcherAssert")) {
      return handleTestFrameworks(paramCount, className, methodName, call);
    }
    else if (TypeUtils.isOptional(owner)) {
      if (DfaOptionalSupport.isOptionalGetMethodName(methodName) || "orElseThrow".equals(methodName)) {
        return Arrays.asList(optionalAbsentContract(fail()), trivialContract(returnNotNull()));
      }
      else if ("isPresent".equals(methodName)) {
        return Arrays.asList(optionalAbsentContract(returnFalse()), trivialContract(returnTrue()));
      }
    }
    else if (MethodUtils.isEquals(method)) {
      return Collections.singletonList(new StandardMethodContract(new ValueConstraint[]{NULL_VALUE}, returnFalse()));
    }

    return Collections.emptyList();
  }

  @NotNull
  private static List<MethodContract> getSubstringContracts(boolean endLimited) {
    List<MethodContract> contracts = new ArrayList<>(5);
    contracts.add(nonnegativeArgumentContract(0));
    contracts.add(specialFieldRangeContract(0, RelationType.LE, SpecialField.STRING_LENGTH));
    if (endLimited) {
      contracts.add(nonnegativeArgumentContract(1));
      contracts.add(specialFieldRangeContract(1, RelationType.LE, SpecialField.STRING_LENGTH));
      contracts.add(MethodContract
                      .singleConditionContract(ContractValue.argument(0), RelationType.LE.getNegated(),
                                               ContractValue.argument(1), fail()));
    }
    return contracts;
  }

  static MethodContract optionalAbsentContract(ContractReturnValue returnValue) {
    return MethodContract
      .singleConditionContract(ContractValue.qualifier(), RelationType.IS, ContractValue.optionalValue(false), returnValue);
  }

  static MethodContract nonnegativeArgumentContract(int argNumber) {
    return MethodContract
      .singleConditionContract(ContractValue.argument(argNumber), RelationType.LT, ContractValue.zero(), fail());
  }

  static MethodContract specialFieldRangeContract(int index, RelationType type, SpecialField specialField) {
    return MethodContract.singleConditionContract(ContractValue.argument(index), type.getNegated(),
                                                  ContractValue.qualifier().specialField(specialField), fail());
  }

  static List<MethodContract> mathMinMax(boolean isMax) {
    return Arrays.asList(MethodContract.singleConditionContract(
      ContractValue.argument(0), isMax ? RelationType.GT : RelationType.LT, ContractValue.argument(1), returnParameter(0)),
                         trivialContract(returnParameter(1)));
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

  private static List<MethodContract> handleTestFrameworks(int paramCount, String className, String methodName,
                                                           @Nullable PsiMethodCallExpression call) {
    if (("assertThat".equals(methodName) || "assumeThat".equals(methodName) || "that".equals(methodName)) && call != null) {
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
    ValueConstraint[] constraints = createConstraintArray(paramCount);
    if ("assertTrue".equals(methodName) || "assumeTrue".equals(methodName)) {
      constraints[checkedParam] = FALSE_VALUE;
      return Collections.singletonList(new StandardMethodContract(constraints, fail()));
    }
    if ("assertFalse".equals(methodName) || "assumeFalse".equals(methodName)) {
      constraints[checkedParam] = TRUE_VALUE;
      return Collections.singletonList(new StandardMethodContract(constraints, fail()));
    }
    if ("assertNull".equals(methodName)) {
      constraints[checkedParam] = NOT_NULL_VALUE;
      return Collections.singletonList(new StandardMethodContract(constraints, fail()));
    }
    if ("assertNotNull".equals(methodName)) {
      return failIfNull(checkedParam, paramCount, false);
    }
    return Collections.emptyList();
  }

  @Nullable
  private static ValueConstraint constraintFromMatcher(PsiExpression expr) {
    if (expr instanceof PsiMethodCallExpression) {
      String calledName = ((PsiMethodCallExpression)expr).getMethodExpression().getReferenceName();
      PsiExpression[] args = ((PsiMethodCallExpression)expr).getArgumentList().getExpressions();
      if (calledName == null) return null;
      switch (calledName) {
        case "array":
        case "arrayWithSize":
        case "arrayContaining":
        case "arrayContainingInAnyOrder":
        case "contains":
        case "containsInAnyOrder":
        case "containsString":
        case "endsWith":
        case "startsWith":
        case "stringContainsInOrder":
        case "empty":
        case "emptyArray":
        case "emptyCollectionOf":
        case "emptyIterable":
        case "emptyIterableOf":
        case "hasEntry":
        case "hasItem":
        case "hasItems":
        case "hasKey":
        case "hasProperty":
        case "hasSize":
        case "hasToString":
        case "hasValue":
        case "hasXPath":
        case "notNullValue":
          return NULL_VALUE;
        case "nullValue":
          return NOT_NULL_VALUE;
        case "equalTo":
          if (args.length == 1) {
            return constraintFromLiteral(args[0]);
          }
          return null;
        case "not":
          if (args.length == 1) {
            ValueConstraint constraint = constraintFromMatcher(args[0]);
            if (constraint != null) {
              return constraint.negate();
            }
          }
          return null;
        case "is":
          if (args.length == 1) {
            ValueConstraint fromMatcher = constraintFromMatcher(args[0]);
            return fromMatcher == null ? constraintFromLiteral(args[0]) : fromMatcher;
          }
          return null;
      }
    }
    return null;
  }

  @Nullable
  private static ValueConstraint constraintFromLiteral(PsiExpression arg) {
    arg = PsiUtil.skipParenthesizedExprDown(arg);
    if (!(arg instanceof PsiLiteralExpression)) return null;
    Object value = ((PsiLiteralExpression)arg).getValue();
    if (value == null) return NOT_NULL_VALUE;
    if (Boolean.TRUE.equals(value)) return FALSE_VALUE;
    if (Boolean.FALSE.equals(value)) return TRUE_VALUE;
    return null;
  }

  @NotNull
  private static List<MethodContract> handleAssertThat(int paramCount, @NotNull PsiMethodCallExpression call) {
    PsiExpression[] args = call.getArgumentList().getExpressions();
    if (args.length == paramCount) {
      for (int i = 1; i < args.length; i++) {
        ValueConstraint constraint = constraintFromMatcher(args[i]);
        if (constraint != null) {
          ValueConstraint[] constraints = createConstraintArray(paramCount);
          constraints[i - 1] = constraint;
          return Collections.singletonList(new StandardMethodContract(constraints, fail()));
        }
      }
      if (args.length == 1 && hasNotNullChainCall(call)) {
        return failIfNull(0, 1, false);
      }
    }
    return Collections.emptyList();
  }

  private static boolean hasNotNullChainCall(PsiMethodCallExpression call) {
    Iterable<PsiElement> exprParents = SyntaxTraverser.psiApi().parents(call).
      takeWhile(e -> !(e instanceof PsiStatement) && !(e instanceof PsiMember));
    return ContainerUtil.exists(exprParents, HardcodedContracts::isNotNullCall);
  }

  private static boolean isNotNullCall(PsiElement ref) {
    return ref instanceof PsiReferenceExpression &&
           "isNotNull".equals(((PsiReferenceExpression)ref).getReferenceName()) &&
           ref.getParent() instanceof PsiMethodCallExpression;
  }

  @NotNull
  private static List<MethodContract> failIfNull(int argIndex, int argCount, boolean returnArg) {
    ValueConstraint[] constraints = createConstraintArray(argCount);
    constraints[argIndex] = NULL_VALUE;
    StandardMethodContract failContract = new StandardMethodContract(constraints, fail());
    if (returnArg) {
      return Arrays.asList(failContract, StandardMethodContract.trivialContract(argCount, returnParameter(argIndex)));
    }
    return Collections.singletonList(failContract);
  }

  public static boolean isHardcodedPure(PsiMethod method) {
    PsiClass aClass = method.getContainingClass();
    if (aClass == null) return false;
    String className = aClass.getQualifiedName();
    if (className == null) return false;
    String name = method.getName();

    if ("java.util.Objects".equals(className) && "requireNonNull".equals(name)) {
      PsiParameter[] parameters = method.getParameterList().getParameters();
      if (parameters.length == 2 && parameters[1].getType().getCanonicalText().contains("Supplier")) {
        return false;
      }
    }

    if ("java.lang.System".equals(className)) {
      return false;
    }
    if (JAVA_UTIL_ARRAYS.equals(className)) {
      return name.equals("binarySearch") || name.equals("spliterator") || name.equals("stream");
    }
    if (QUEUE_POLL.methodMatches(method)) {
      return false;
    }
    return true;
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
