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

import com.intellij.codeInspection.dataFlow.value.DfaOptionalValue;
import com.intellij.codeInspection.dataFlow.value.DfaRelationValue.RelationType;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.SpecialField;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.MethodUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import one.util.streamex.IntStreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.intellij.codeInspection.dataFlow.MethodContract.ValueConstraint.*;
import static com.intellij.codeInspection.dataFlow.StandardMethodContract.createConstraintArray;

/**
 * @author peter
 */
public class HardcodedContracts {
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
        return Arrays.asList(new NonnegativeArgumentContract(1, 0),
                             new SpecialFieldRangeContract(1, 0, RelationType.LT, SpecialField.STRING_LENGTH));
      }
      else if (("substring".equals(methodName) || "subSequence".equals(methodName)) && paramCount <= 2) {
        List<MethodContract> contracts = new ArrayList<>(5);
        contracts.add(new NonnegativeArgumentContract(paramCount, 0));
        contracts.add(new SpecialFieldRangeContract(paramCount, 0, RelationType.LE, SpecialField.STRING_LENGTH));
        if (paramCount == 2) {
          contracts.add(new NonnegativeArgumentContract(paramCount, 1));
          contracts.add(new SpecialFieldRangeContract(paramCount, 1, RelationType.LE, SpecialField.STRING_LENGTH));
          contracts.add(new ArgumentRelationContract(paramCount, 0, RelationType.LE, 1));
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
    else if (MethodUtils.methodMatches(method, CommonClassNames.JAVA_UTIL_COLLECTION, null, "get", PsiType.INT)) {
      return Arrays.asList(new NonnegativeArgumentContract(paramCount, 0),
                           new SpecialFieldRangeContract(paramCount, 0, RelationType.LT, SpecialField.COLLECTION_SIZE));
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
        return Arrays.asList(new OptionalPresenceContract(false, THROW_EXCEPTION),
                             new OptionalPresenceContract(true, NOT_NULL_VALUE));
      }
      else if ("isPresent".equals(methodName)) {
        return Arrays.asList(new OptionalPresenceContract(false, FALSE_VALUE),
                             new OptionalPresenceContract(true, TRUE_VALUE));
      }
    }

    return Collections.emptyList();
  }

  static class OptionalPresenceContract extends MethodContract {
    private final boolean myPresent;
    private final ValueConstraint myReturnValue;

    public OptionalPresenceContract(boolean mustPresent, ValueConstraint returnValue) {
      myReturnValue = returnValue;
      myPresent = mustPresent;
    }

    @Override
    protected List<DfaValue> getConditions(DfaValueFactory factory, DfaValue qualifier, DfaValue[] arguments) {
      DfaOptionalValue optional = factory.getOptionalFactory().getOptional(myPresent);
      return Collections.singletonList(factory.createCondition(qualifier, RelationType.IS, optional));
    }

    @Override
    protected String getArgumentsPresentation() {
      return "[" + (myPresent ? "present" : "absent") + "]";
    }

    @Override
    public ValueConstraint getReturnValue() {
      return myReturnValue;
    }
  }

  static abstract class ArgumentRangeContract extends MethodContract {
    final int myParamCount;
    final int myIndex;
    final RelationType myRelationType;

    ArgumentRangeContract(int paramCount, int index, RelationType type) {
      myParamCount = paramCount;
      myIndex = index;
      myRelationType = type;
    }

    @Override
    protected List<DfaValue> getConditions(DfaValueFactory factory, DfaValue qualifier, DfaValue[] arguments) {
      DfaValue left = arguments[myIndex];
      DfaValue right = getBound(factory, qualifier, arguments);
      return Collections.singletonList(factory.createCondition(left, myRelationType.getNegated(), right));
    }

    @NotNull
    abstract DfaValue getBound(DfaValueFactory factory, DfaValue qualifier, DfaValue[] arguments);

    abstract String getBoundRepresentation();

    @Override
    protected String getArgumentsPresentation() {
      return IntStreamEx.range(myParamCount)
        .mapToObj(idx -> idx == myIndex ? myRelationType.getNegated() + getBoundRepresentation() : "_")
        .joining(", ");
    }

    @Override
    public ValueConstraint getReturnValue() {
      return THROW_EXCEPTION;
    }
  }

  static class NonnegativeArgumentContract extends ArgumentRangeContract {
    public NonnegativeArgumentContract(int paramCount, int nonNegativeArgumentIndex) {
      super(paramCount, nonNegativeArgumentIndex, RelationType.GE);
    }

    @NotNull
    @Override
    DfaValue getBound(DfaValueFactory factory, DfaValue qualifier, DfaValue[] arguments) {
      return factory.getConstFactory().createFromValue(0, PsiType.INT, null);
    }

    @Override
    String getBoundRepresentation() {
      return "0";
    }
  }

  static class ArgumentRelationContract extends ArgumentRangeContract {
    private final int myIndex;

    public ArgumentRelationContract(int paramCount, int leftIndex, RelationType relationType, int rightIndex) {
      super(paramCount, leftIndex, relationType);
      myIndex = rightIndex;
    }

    @NotNull
    @Override
    DfaValue getBound(DfaValueFactory factory, DfaValue qualifier, DfaValue[] arguments) {
      return arguments[myIndex];
    }

    @Override
    String getBoundRepresentation() {
      return "arg#" + myIndex;
    }
  }

  static class SpecialFieldRangeContract extends ArgumentRangeContract {
    private final SpecialField mySpecialField;

    SpecialFieldRangeContract(int paramCount, int index, RelationType type, SpecialField specialField) {
      super(paramCount, index, type);
      mySpecialField = specialField;
    }

    @NotNull
    @Override
    DfaValue getBound(DfaValueFactory factory, DfaValue qualifier, DfaValue[] arguments) {
      return mySpecialField.createValue(factory, qualifier);
    }

    @Override
    String getBoundRepresentation() {
      return "this."+mySpecialField.getMethodName()+"()";
    }
  }

  private static boolean isJunit(String className) {
    return className.startsWith("junit.framework.") || className.startsWith("org.junit.");
  }

  private static boolean isTestng(String className) {
    return className.startsWith("org.testng.");
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

    int checkedParam = testng ? 0 : paramCount - 1;
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
    String qName = PsiUtil.getMemberQualifiedName(method);
    if ("java.lang.System.exit".equals(qName)) {
      return false;
    }

    if ("java.util.Objects.requireNonNull".equals(qName)) {
      PsiParameter[] parameters = method.getParameterList().getParameters();
      if (parameters.length == 2 && parameters[1].getType().getCanonicalText().contains("Supplier")) {
        return false;
      }
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
