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

import com.intellij.codeInspection.dataFlow.value.DfaRelationValue.RelationType;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.MethodUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import static com.intellij.codeInspection.dataFlow.MethodContract.ValueConstraint.*;
import static com.intellij.codeInspection.dataFlow.StandardMethodContract.createConstraintArray;

/**
 * @author peter
 */
public class HardcodedContracts {
  private static final Pattern FIRST_OR_LAST = Pattern.compile("first|last");
  private static final Pattern CONTAINS_KEY_VALUE = Pattern.compile("containsKey|containsValue");
  // All these methods take array as 1st parameter, from index as 2nd and to index as 3rd
  // thus ARRAY_RANGE_CONTRACTS are applicable to them
  private static final Pattern ARRAY_RANGED_METHODS =
    Pattern.compile("binarySearch|fill|parallelPrefix|parallelSort|sort|spliterator|stream");

  private static final List<MethodContract> ARRAY_RANGE_CONTRACTS = ContainerUtil.immutableList(
    nonnegativeArgumentContract(1),
    nonnegativeArgumentContract(2),
    MethodContract.singleConditionContract(ContractValue.argument(1), RelationType.GT,
                                           ContractValue.argument(0).specialField(SpecialField.ARRAY_LENGTH), THROW_EXCEPTION),
    MethodContract.singleConditionContract(ContractValue.argument(2), RelationType.GT,
                                           ContractValue.argument(0).specialField(SpecialField.ARRAY_LENGTH), THROW_EXCEPTION),
    MethodContract.singleConditionContract(ContractValue.argument(1), RelationType.GT,
                                           ContractValue.argument(2), THROW_EXCEPTION)
  );

  public static List<MethodContract> getHardcodedContracts(@NotNull PsiMethod method, @Nullable PsiMethodCallExpression call) {
    PsiClass owner = method.getContainingClass();
    if (owner == null ||
        InjectedLanguageManager.getInstance(owner.getProject()).isInjectedFragment(owner.getContainingFile())) {
      return Collections.emptyList();
    }

    final int paramCount = method.getParameterList().getParametersCount();
    String className = owner.getQualifiedName();
    if (className == null) return Collections.emptyList();

    String methodName = method.getName();

    if ("java.lang.System".equals(className)) {
      if ("exit".equals(methodName)) {
        return Collections.singletonList(new StandardMethodContract(createConstraintArray(paramCount), THROW_EXCEPTION));
      }
    }
    else if ("com.google.common.base.Preconditions".equals(className)) {
      if ("checkNotNull".equals(methodName) && paramCount > 0) {
        return failIfNull(0, paramCount);
      }
      if (("checkArgument".equals(methodName) || "checkState".equals(methodName)) && paramCount > 0) {
        MethodContract.ValueConstraint[] constraints = createConstraintArray(paramCount);
        constraints[0] = FALSE_VALUE;
        return Collections.singletonList(new StandardMethodContract(constraints, THROW_EXCEPTION));
      }
    }
    else if ("java.util.Objects".equals(className)) {
      if ("requireNonNull".equals(methodName) && paramCount > 0) {
        return failIfNull(0, paramCount);
      }
    }
    else if (CommonClassNames.JAVA_LANG_STRING.equals(className)) {
      if (("charAt".equals(methodName) || "codePointAt".equals(methodName)) && paramCount == 1) {
        return Arrays.asList(nonnegativeArgumentContract(0),
                             specialFieldRangeContract(0, RelationType.LT, SpecialField.STRING_LENGTH));
      }
      else if (("substring".equals(methodName) || "subSequence".equals(methodName)) && paramCount <= 2) {
        List<MethodContract> contracts = new ArrayList<>(5);
        contracts.add(nonnegativeArgumentContract(0));
        contracts.add(specialFieldRangeContract(0, RelationType.LE, SpecialField.STRING_LENGTH));
        if (paramCount == 2) {
          contracts.add(nonnegativeArgumentContract(1));
          contracts.add(specialFieldRangeContract(1, RelationType.LE, SpecialField.STRING_LENGTH));
          contracts.add(MethodContract
                          .singleConditionContract(ContractValue.argument(0), RelationType.LE.getNegated(), ContractValue.argument(1),
                                                   THROW_EXCEPTION));
        }
        return contracts;
      }
      else if ("isEmpty".equals(methodName) && paramCount == 0) {
        return SpecialField.STRING_LENGTH.getEmptyContracts();
      }
    }
    else if (MethodUtils.methodMatches(method, CommonClassNames.JAVA_UTIL_COLLECTION, PsiType.BOOLEAN, "isEmpty")) {
      return SpecialField.COLLECTION_SIZE.getEmptyContracts();
    }
    else if (MethodUtils.methodMatches(method, CommonClassNames.JAVA_UTIL_COLLECTION, PsiType.BOOLEAN, "contains", (PsiType)null)) {
      return Collections.singletonList(MethodContract.singleConditionContract(
        ContractValue.qualifier().specialField(SpecialField.COLLECTION_SIZE), RelationType.EQ, ContractValue.zero(), FALSE_VALUE));
    }
    else if (MethodUtils.methodMatches(method, CommonClassNames.JAVA_UTIL_SET, PsiType.BOOLEAN, "equals", (PsiType)null) ||
             MethodUtils.methodMatches(method, CommonClassNames.JAVA_UTIL_LIST, PsiType.BOOLEAN, "equals", (PsiType)null)) {
      return Collections.singletonList(MethodContract.singleConditionContract(
        ContractValue.qualifier().specialField(SpecialField.COLLECTION_SIZE), RelationType.NE,
        ContractValue.argument(0).specialField(SpecialField.COLLECTION_SIZE), FALSE_VALUE));
    }
    else if (MethodUtils.methodMatches(method, CommonClassNames.JAVA_UTIL_LIST, null, "get", PsiType.INT)) {
      return Arrays.asList(nonnegativeArgumentContract(0),
                           specialFieldRangeContract(0, RelationType.LT, SpecialField.COLLECTION_SIZE));
    }
    else if (MethodUtils.methodMatches(method, "java.util.SortedSet", null, FIRST_OR_LAST)) {
      return Collections.singletonList(MethodContract.singleConditionContract(
        ContractValue.qualifier().specialField(SpecialField.COLLECTION_SIZE), RelationType.EQ,
        ContractValue.zero(), THROW_EXCEPTION));
    }
    else if (MethodUtils.methodMatches(method, CommonClassNames.JAVA_UTIL_MAP, PsiType.BOOLEAN, "isEmpty")) {
      return SpecialField.MAP_SIZE.getEmptyContracts();
    }
    else if (MethodUtils.methodMatches(method, CommonClassNames.JAVA_UTIL_MAP, PsiType.BOOLEAN, CONTAINS_KEY_VALUE, (PsiType)null)) {
      return Collections.singletonList(MethodContract.singleConditionContract(
        ContractValue.qualifier().specialField(SpecialField.MAP_SIZE), RelationType.EQ, ContractValue.zero(), FALSE_VALUE));
    }
    else if (MethodUtils.methodMatches(method, CommonClassNames.JAVA_UTIL_MAP, PsiType.BOOLEAN, "equals", (PsiType)null)) {
      return Collections.singletonList(MethodContract.singleConditionContract(
        ContractValue.qualifier().specialField(SpecialField.MAP_SIZE), RelationType.NE,
        ContractValue.argument(0).specialField(SpecialField.MAP_SIZE), FALSE_VALUE));
    }
    else if (MethodUtils.methodMatches(method, CommonClassNames.JAVA_UTIL_ARRAYS, null, ARRAY_RANGED_METHODS, (PsiType[])null) &&
      paramCount >= 3) {
      return ARRAY_RANGE_CONTRACTS;
    }
    else if ("org.apache.commons.lang.Validate".equals(className) ||
             "org.apache.commons.lang3.Validate".equals(className) ||
             "org.springframework.util.Assert".equals(className)) {
      if (("isTrue".equals(methodName) || "state".equals(methodName)) && paramCount > 0) {
        MethodContract.ValueConstraint[] constraints = createConstraintArray(paramCount);
        constraints[0] = FALSE_VALUE;
        return Collections.singletonList(new StandardMethodContract(constraints, THROW_EXCEPTION));
      }
      if ("notNull".equals(methodName) && paramCount > 0) {
        MethodContract.ValueConstraint[] constraints = createConstraintArray(paramCount);
        constraints[0] = NULL_VALUE;
        return Collections.singletonList(new StandardMethodContract(constraints, THROW_EXCEPTION));
      }
    }
    else if (isJunit(className) || isTestng(className) ||
             className.startsWith("com.google.common.truth.") ||
             className.startsWith("org.assertj.core.api.")) {
      return handleTestFrameworks(paramCount, className, methodName, call);
    }
    else if (TypeUtils.isOptional(owner)) {
      if (DfaOptionalSupport.isOptionalGetMethodName(methodName) || "orElseThrow".equals(methodName)) {
        return Arrays.asList(optionalAbsentContract(THROW_EXCEPTION), MethodContract.trivialContract(NOT_NULL_VALUE));
      }
      else if ("isPresent".equals(methodName)) {
        return Arrays.asList(optionalAbsentContract(FALSE_VALUE), MethodContract.trivialContract(TRUE_VALUE));
      }
    }

    return Collections.emptyList();
  }

  static MethodContract optionalAbsentContract(MethodContract.ValueConstraint returnValue) {
    return MethodContract
      .singleConditionContract(ContractValue.qualifier(), RelationType.IS, ContractValue.optionalValue(false), returnValue);
  }

  static MethodContract nonnegativeArgumentContract(int argNumber) {
    return MethodContract
      .singleConditionContract(ContractValue.argument(argNumber), RelationType.LT, ContractValue.zero(), THROW_EXCEPTION);
  }

  static MethodContract specialFieldRangeContract(int index, RelationType type, SpecialField specialField) {
    return MethodContract.singleConditionContract(ContractValue.argument(index), type.getNegated(),
                                                  ContractValue.qualifier().specialField(specialField), THROW_EXCEPTION);
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

  private static boolean isNotNullMatcher(PsiExpression expr) {
    if (expr instanceof PsiMethodCallExpression) {
      String calledName = ((PsiMethodCallExpression)expr).getMethodExpression().getReferenceName();
      if ("notNullValue".equals(calledName)) {
        return true;
      }
      if ("not".equals(calledName)) {
        PsiExpression[] notArgs = ((PsiMethodCallExpression)expr).getArgumentList().getExpressions();
        if (notArgs.length == 1 &&
            notArgs[0] instanceof PsiMethodCallExpression &&
            "equalTo".equals(((PsiMethodCallExpression)notArgs[0]).getMethodExpression().getReferenceName())) {
          PsiExpression[] equalArgs = ((PsiMethodCallExpression)notArgs[0]).getArgumentList().getExpressions();
          if (equalArgs.length == 1 && ExpressionUtils.isNullLiteral(equalArgs[0])) {
            return true;
          }
        }
      }
      if ("is".equals(calledName)) {
        PsiExpression[] args = ((PsiMethodCallExpression)expr).getArgumentList().getExpressions();
        if (args.length == 1) return isNotNullMatcher(args[0]);
      }
    }
    return false;
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
      return Collections.singletonList(new StandardMethodContract(createConstraintArray(paramCount), THROW_EXCEPTION));
    }

    if (paramCount == 0) return Collections.emptyList();

    int checkedParam = testng || isJunit5(className) ? 0 : paramCount - 1;
    MethodContract.ValueConstraint[] constraints = createConstraintArray(paramCount);
    if ("assertTrue".equals(methodName) || "assumeTrue".equals(methodName)) {
      constraints[checkedParam] = FALSE_VALUE;
      return Collections.singletonList(new StandardMethodContract(constraints, THROW_EXCEPTION));
    }
    if ("assertFalse".equals(methodName) || "assumeFalse".equals(methodName)) {
      constraints[checkedParam] = TRUE_VALUE;
      return Collections.singletonList(new StandardMethodContract(constraints, THROW_EXCEPTION));
    }
    if ("assertNull".equals(methodName)) {
      constraints[checkedParam] = NOT_NULL_VALUE;
      return Collections.singletonList(new StandardMethodContract(constraints, THROW_EXCEPTION));
    }
    if ("assertNotNull".equals(methodName) || "assumeNotNull".equals(methodName)) {
      return failIfNull(checkedParam, paramCount);
    }
    return Collections.emptyList();
  }

  @NotNull
  private static List<MethodContract> handleAssertThat(int paramCount, @NotNull PsiMethodCallExpression call) {
    PsiExpression[] args = call.getArgumentList().getExpressions();
    if (args.length == paramCount) {
      for (int i = 1; i < args.length; i++) {
        if (isNotNullMatcher(args[i])) {
          return failIfNull(i - 1, paramCount);
        }
      }
      if (args.length == 1 && hasNotNullChainCall(call)) {
        return failIfNull(0, 1);
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
  private static List<MethodContract> failIfNull(int argIndex, int argCount) {
    MethodContract.ValueConstraint[] constraints = createConstraintArray(argCount);
    constraints[argIndex] = NULL_VALUE;
    return Collections.singletonList(new StandardMethodContract(constraints, THROW_EXCEPTION));
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
    if (CommonClassNames.JAVA_UTIL_ARRAYS.equals(className)) {
      return name.equals("binarySearch") || name.equals("spliterator") || name.equals("stream");
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
