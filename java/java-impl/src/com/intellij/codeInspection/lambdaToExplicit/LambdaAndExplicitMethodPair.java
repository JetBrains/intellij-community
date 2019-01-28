// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.lambdaToExplicit;

import com.intellij.codeInspection.util.LambdaGenerationUtil;
import com.intellij.codeInspection.util.OptionalUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import one.util.streamex.StreamEx;
import org.intellij.lang.annotations.RegExp;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Pattern;

import static com.intellij.psi.CommonClassNames.JAVA_LANG_STRING;

class LambdaAndExplicitMethodPair {
  static final LambdaAndExplicitMethodPair[] INFOS = {
    new LambdaAndExplicitMethodPair(CommonClassNames.JAVA_UTIL_MAP, "computeIfAbsent", "putIfAbsent", 1, "V", false, "k"),
    new LambdaAndExplicitMethodPair(CommonClassNames.JAVA_UTIL_OPTIONAL, "orElseGet", "orElse", 0, "T", true),
    new LambdaAndExplicitMethodPair(OptionalUtil.OPTIONAL_INT, "orElseGet", "orElse", 0, "int", true),
    new LambdaAndExplicitMethodPair(OptionalUtil.OPTIONAL_LONG, "orElseGet", "orElse", 0, "long", true),
    new LambdaAndExplicitMethodPair(OptionalUtil.OPTIONAL_DOUBLE, "orElseGet", "orElse", 0, "double", true),
    new LambdaAndExplicitMethodPair(OptionalUtil.GUAVA_OPTIONAL, "or", "*", 0, "T", true),
    new LambdaAndExplicitMethodPair("java.util.Objects", "requireNonNull", "*", 1, JAVA_LANG_STRING, true),
    new LambdaAndExplicitMethodPair("java.util.Objects", "requireNonNullElseGet", "requireNonNullElse", 1, "T", true),
    new LambdaAndExplicitMethodPair("org.junit.jupiter.api.Assertions", "assert(?!Timeout).*|fail", "*", -1, JAVA_LANG_STRING, true),
    new LambdaAndExplicitMethodPair("org.junit.jupiter.api.Assertions", "assert(True|False)", "*", 0, JAVA_LANG_STRING, true),
    new LambdaAndExplicitMethodPair(CommonClassNames.JAVA_UTIL_ARRAYS, "setAll", "fill", 1, null, true, "i")
  };
  private final @NotNull String myClass;
  private final @NotNull Pattern myLambdaMethod;
  private final @NotNull String myExplicitMethod;
  private final int myParameterIndex;
  private final @Nullable String myExplicitParameterType;
  private final boolean myCanUseReturnValue;
  private final @NotNull String[] myDefaultLambdaParameters;

  /**
   * @param aClass                class containing both methods
   * @param lambdaMethod          regexp to match the name of the method which accepts lambda argument
   * @param explicitMethod        name of the equivalent method ("*" if name is the same as lambdaMethod)
   *                              accepting constant instead of lambda argument (all other args must be the same)
   * @param index                 index of lambda argument, zero-based, or -1 to denote the last argument
   * @param explicitParameterType type of explicit parameter (null if explicit -> lambda conversion is not applicable)
   * @param canUseReturnValue     true if method return value does not depend on whether lambda or constant version is used
   */
  LambdaAndExplicitMethodPair(@NotNull String aClass,
                              @NotNull @RegExp String lambdaMethod,
                              @NotNull String explicitMethod,
                              int index,
                              @Nullable String explicitParameterType,
                              boolean canUseReturnValue,
                              @NotNull String... defaultLambdaParameters) {
    myClass = aClass;
    myLambdaMethod = Pattern.compile(lambdaMethod);
    myExplicitMethod = explicitMethod;
    myParameterIndex = index;
    myExplicitParameterType = explicitParameterType;
    myCanUseReturnValue = canUseReturnValue;
    myDefaultLambdaParameters = defaultLambdaParameters.length == 0 ? ArrayUtil.EMPTY_STRING_ARRAY : defaultLambdaParameters;
  }

  boolean isLambdaCall(PsiMethodCallExpression lambdaCall, PsiLambdaExpression lambda) {
    String name = lambdaCall.getMethodExpression().getReferenceName();
    if (name == null || !myLambdaMethod.matcher(name).matches()) return false;
    if (!myCanUseReturnValue && !(lambdaCall.getParent() instanceof PsiExpressionStatement)) return false;
    PsiExpression[] args = lambdaCall.getArgumentList().getExpressions();
    if (args.length == 0) return false;
    int index = myParameterIndex == -1 ? args.length - 1 : myParameterIndex;
    if (args.length <= index || args[index] != lambda) return false;
    PsiMethod method = lambdaCall.resolveMethod();
    if (method == null) return false;
    PsiParameter[] parameters = method.getParameterList().getParameters();
    if (parameters.length <= index) return false;
    PsiClass fnClass = PsiUtil.resolveClassInClassTypeOnly(parameters[index].getType());
    return fnClass != null && LambdaUtil.getFunction(fnClass) != null &&
           InheritanceUtil.isInheritor(method.getContainingClass(), false, myClass);
  }

  PsiExpression getLambdaCandidateFromExplicitCall(PsiMethodCallExpression explicitCall) {
    if (myExplicitParameterType == null) return null;
    String name = explicitCall.getMethodExpression().getReferenceName();
    if (name == null) return null;
    if (myExplicitMethod.equals("*")) {
      if (!myLambdaMethod.matcher(name).matches()) return null;
    }
    else if (!myExplicitMethod.equals(name)) {
      return null;
    }
    if (!myCanUseReturnValue && !(explicitCall.getParent() instanceof PsiExpressionStatement)) return null;
    PsiExpression[] args = explicitCall.getArgumentList().getExpressions();
    if (args.length == 0) return null;
    int index = myParameterIndex == -1 ? args.length - 1 : myParameterIndex;
    if (args.length <= index) return null;
    PsiExpression arg = args[index];
    if (arg instanceof PsiFunctionalExpression) return null;
    if (!LambdaGenerationUtil.canBeUncheckedLambda(arg)) return null;
    PsiMethod method = explicitCall.resolveMethod();
    if (method == null) return null;
    PsiParameter[] parameters = method.getParameterList().getParameters();
    if (parameters.length <= index) return null;
    PsiType type = parameters[index].getType();
    if (!type.equalsToText(myExplicitParameterType)) return null;
    if (!InheritanceUtil.isInheritor(method.getContainingClass(), false, myClass)) return null;
    return arg;
  }

  public String getExplicitMethodName(PsiMethodCallExpression lambdaCall) {
    if (myExplicitMethod.equals("*")) {
      return lambdaCall.getMethodExpression().getReferenceName();
    }
    return myExplicitMethod;
  }

  public String getLambdaMethodName(PsiMethodCallExpression explicitCall) {
    if (myExplicitMethod.equals("*")) {
      return explicitCall.getMethodExpression().getReferenceName();
    }
    return myLambdaMethod.pattern();
  }

  @NotNull
  public String makeLambda(@NotNull PsiExpression expression) {
    if (myDefaultLambdaParameters.length == 0) {
      return "()->" + expression.getText();
    }
    JavaCodeStyleManager manager = JavaCodeStyleManager.getInstance(expression.getProject());
    String params = StreamEx.of(myDefaultLambdaParameters)
                            .map(param -> manager.suggestUniqueVariableName(param, expression, true))
                            .joining(",");
    if (myDefaultLambdaParameters.length != 1) {
      params = "(" + params + ")";
    }
    return params + "->" + expression.getText();
  }
}
