// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.UpdateInspectionOptionFix;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.junit.JUnitCommonClassNames;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.LibraryUtil;
import com.siyeh.ig.psiutils.SideEffectChecker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

/**
 * @author Bas Leijdekkers
 */
public final class EqualsWithItselfInspection extends BaseInspection {

  private static final CallMatcher TWO_ARGUMENT_COMPARISON = CallMatcher.anyOf(
    CallMatcher.staticCall(CommonClassNames.JAVA_UTIL_OBJECTS, "equals", "deepEquals"),
    CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_COMPARATOR, "compare"),
    CallMatcher.staticCall(CommonClassNames.JAVA_UTIL_ARRAYS, "equals", "deepEquals"),
    CallMatcher.staticCall(CommonClassNames.JAVA_LANG_INTEGER, "compare", "compareUnsigned"),
    CallMatcher.staticCall(CommonClassNames.JAVA_LANG_LONG, "compare", "compareUnsigned"),
    CallMatcher.staticCall(CommonClassNames.JAVA_LANG_SHORT, "compare", "compareUnsigned"),
    CallMatcher.staticCall(CommonClassNames.JAVA_LANG_BYTE, "compare", "compareUnsigned"),
    CallMatcher.staticCall(CommonClassNames.JAVA_LANG_BOOLEAN, "compare"),
    CallMatcher.staticCall(CommonClassNames.JAVA_LANG_CHARACTER, "compare"),
    CallMatcher.staticCall(CommonClassNames.JAVA_LANG_FLOAT, "compare"),
    CallMatcher.staticCall(CommonClassNames.JAVA_LANG_DOUBLE, "compare")
  );

  private static final CallMatcher ASSERT_ARGUMENT_COMPARISON = CallMatcher.anyOf(
    CallMatcher.staticCall(JUnitCommonClassNames.ORG_JUNIT_ASSERT, "assertEquals", "assertArrayEquals", "assertIterableEquals",
                           "assertLinesMatch", "assertNotEquals"),
    CallMatcher.staticCall(JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_ASSERTIONS, "assertEquals", "assertArrayEquals", "assertIterableEquals",
                           "assertLinesMatch", "assertNotEquals"),
    CallMatcher.staticCall("org.testng.Assert", "assertEquals", "assertEqualsDeep", "assertEqualsNoOrder", "assertNotEquals",
                           "assertNotEqualsDeep"),
    CallMatcher.staticCall("org.testng.AssertJUnit", "assertEquals"),
    CallMatcher.staticCall("org.testng.internal.junit.ArrayAsserts", "assertArrayEquals")
  );

  private static final CallMatcher ASSERT_ARGUMENTS_THE_SAME = CallMatcher.anyOf(
    CallMatcher.staticCall(JUnitCommonClassNames.ORG_JUNIT_ASSERT, "assertSame", "assertNotSame"),
    CallMatcher.staticCall(JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_ASSERTIONS, "assertSame", "assertNotSame"),
    CallMatcher.staticCall("org.testng.Assert", "assertSame", "assertNotSame"),
    CallMatcher.staticCall("org.testng.AssertJUnit", "assertSame", "assertNotSame")
  );

  private static final CallMatcher ONE_ARGUMENT_COMPARISON = CallMatcher.anyOf(
    CallMatcher.instanceCall(CommonClassNames.JAVA_LANG_OBJECT, "equals"),
    CallMatcher.instanceCall(CommonClassNames.JAVA_LANG_STRING, "equalsIgnoreCase", "compareToIgnoreCase"),
    CallMatcher.instanceCall(CommonClassNames.JAVA_LANG_COMPARABLE, "compareTo")
  );

  private static final CallMatcher ASSERTJ_COMPARISON =
    CallMatcher.instanceCall("org.assertj.core.api.AbstractAssert", "isEqualTo", "isNotEqualTo").parameterCount(1);

  private static final CallMatcher ASSERTJ_THE_SAME =
    CallMatcher.instanceCall("org.assertj.core.api.AbstractAssert", "isSameAs", "isNotSameAs").parameterCount(1);


  private static final CallMatcher ASSERTJ_ASSERT_THAT =
    CallMatcher.staticCall("org.assertj.core.api.Assertions", "assertThat").parameterCount(1);

  private static final CallMatcher POSSIBLE_TO_SKIP_TEST_ASSERTIONS = CallMatcher.anyOf(ASSERT_ARGUMENT_COMPARISON, ASSERTJ_COMPARISON);

  @SuppressWarnings("PublicField")
  public boolean ignoreNonFinalClassesInTest = false;

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("ignoreNonFinalClassesInTest", InspectionGadgetsBundle.message(
        "equals.with.itself.option")));
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("equals.with.itself.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new EqualsWithItselfVisitor();
  }

  @Override
  @Nullable
  protected LocalQuickFix buildFix(Object... infos) {
    final Boolean canEnableOption = (Boolean)infos[0];
    return canEnableOption ? LocalQuickFix.from(new UpdateInspectionOptionFix(
      this, "ignoreNonFinalClassesInTest", InspectionGadgetsBundle.message("equals.with.itself.option"), true)) : null;
  }

  private class EqualsWithItselfVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      if (ignoreNonFinalClassesInTest &&
          POSSIBLE_TO_SKIP_TEST_ASSERTIONS.test(expression) &&
          !isFinalLibraryClassOrPrimitives(expression)) {
        return;
      }
      if (isEqualsWithItself(expression)) {
        registerMethodCallError(expression, !ignoreNonFinalClassesInTest &&
                                            POSSIBLE_TO_SKIP_TEST_ASSERTIONS.test(expression) &&
                                            !isFinalLibraryClassOrPrimitives(expression));
      }
    }

    private static boolean isFinalLibraryClassOrPrimitives(PsiMethodCallExpression expression) {
      final PsiExpressionList argumentList = expression.getArgumentList();
      if (argumentList.getExpressionCount() < 1) return false;
      PsiType type = argumentList.getExpressions()[0].getType();
      if (type == null) return false;

      if (TypeConversionUtil.isPrimitiveAndNotNull(type)) return true;
      if (type instanceof PsiArrayType) return true;
      PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(type);
      if (aClass == null) return false;
      if (!aClass.hasModifierProperty(PsiModifier.FINAL)) return false;
      return LibraryUtil.classIsInLibrary(aClass);
    }
  }

  public static boolean isEqualsWithItself(PsiMethodCallExpression expression) {
    final PsiReferenceExpression methodExpression = expression.getMethodExpression();
    final PsiExpressionList argumentList = expression.getArgumentList();
    final int count = argumentList.getExpressionCount();
    if (count == 1) {
      final PsiExpression qualifier = ExpressionUtils.getEffectiveQualifier(methodExpression);
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length != 1) return false;
      final PsiExpression argument = PsiUtil.skipParenthesizedExprDown(arguments[0]);
      if (ONE_ARGUMENT_COMPARISON.test(expression)) {
        return isItself(qualifier, argument);
      }
      else if (ASSERTJ_COMPARISON.test(expression)) {
        return isItself(getAssertThatArgument(expression), argument);
      }
      else if (ASSERTJ_THE_SAME.test(expression)) {
        return isTheSame(getAssertThatArgument(expression), argument);
      }
    }
    else if (count == 2 || count == 3) {
      final PsiExpression[] arguments = argumentList.getExpressions();
      final PsiExpression firstArgument = PsiUtil.skipParenthesizedExprDown(arguments[0]);
      final PsiExpression secondArgument = PsiUtil.skipParenthesizedExprDown(arguments[1]);

      if (TWO_ARGUMENT_COMPARISON.test(expression) || ASSERT_ARGUMENT_COMPARISON.test(expression)) {
        return isItself(firstArgument, secondArgument);
      }
      else if (ASSERT_ARGUMENTS_THE_SAME.test(expression)) {
        return isTheSame(firstArgument, secondArgument);
      }
    }
    return false;
  }

  @Nullable
  private static PsiExpression getAssertThatArgument(@Nullable PsiExpression expression) {
    while (expression instanceof PsiMethodCallExpression callExpression) {
      if (ASSERTJ_ASSERT_THAT.test(callExpression)) return callExpression.getArgumentList().getExpressions()[0];
      PsiReferenceExpression reference = callExpression.getMethodExpression();
      expression = reference.getQualifierExpression();
      expression = PsiUtil.skipParenthesizedExprDown(expression);
    }
    return null;
  }

  private static boolean isTheSame(@Nullable PsiExpression left, @Nullable PsiExpression right) {
    return isItself(left, right) && !ExpressionUtils.isNewObject(left);
  }

  private static boolean isItself(@Nullable PsiExpression left, @Nullable PsiExpression right) {
    return left != null && right != null &&
           EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(left, right) &&
           !SideEffectChecker.mayHaveSideEffects(left);
  }
}