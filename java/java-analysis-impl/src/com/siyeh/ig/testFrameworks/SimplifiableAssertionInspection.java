// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.testFrameworks;

import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.java.library.JavaLibraryUtil;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.*;
import com.intellij.util.text.VersionComparatorUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.junit.JUnitCommonClassNames;
import com.siyeh.ig.psiutils.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.stream.IntStream;

public final class SimplifiableAssertionInspection extends BaseInspection implements CleanupLocalInspectionTool {
  @Override
  protected @NotNull String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("simplifiable.junit.assertion.problem.descriptor", infos[0]);
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public LocalQuickFix buildFix(Object... infos) {
    return new SimplifyAssertFix();
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new SimplifiableJUnitAssertionVisitor();
  }

  private static boolean isAssertThatCouldBeFail(PsiExpression position, boolean checkTrue) {
    return (checkTrue ? JavaKeywords.TRUE : JavaKeywords.FALSE).equals(position.getText());
  }

  private static boolean isAssertEqualsThatCouldBeAssertLiteral(AssertHint assertHint) {
    final PsiExpression firstTestArgument = assertHint.getFirstArgument();
    final PsiExpression secondTestArgument = assertHint.getSecondArgument();
    return isSimpleLiteral(firstTestArgument, secondTestArgument) ||
           isSimpleLiteral(secondTestArgument, firstTestArgument);
  }

  private static boolean isSimpleLiteral(PsiExpression expression1, PsiExpression expression2) {
    if (!(expression1 instanceof PsiLiteralExpression) || expression2 == null) {
      return false;
    }
    final String text = expression1.getText();
    if (JavaKeywords.NULL.equals(text)) {
      return true;
    }
    if (!JavaKeywords.TRUE.equals(text) && !JavaKeywords.FALSE.equals(text)) {
      return false;
    }
    final PsiType type = expression2.getType();
    return PsiTypes.booleanType().equals(type);
  }

  private static boolean isEqualityComparison(PsiExpression expression) {
    if (expression instanceof PsiBinaryExpression binaryExpression) {
      final IElementType tokenType = binaryExpression.getOperationTokenType();
      if (!tokenType.equals(JavaTokenType.EQEQ)) {
        return false;
      }
      final PsiExpression lhs = binaryExpression.getLOperand();
      final PsiExpression rhs = binaryExpression.getROperand();
      if (rhs == null) {
        return false;
      }
      final PsiType type = lhs.getType();
      return type != null && TypeConversionUtil.isPrimitiveAndNotNullOrWrapper(type);
    }
    return EqualityCheck.from(expression) != null;
  }

  private static final CallMatcher ARRAYS_EQUALS = CallMatcher.staticCall("java.util.Arrays", "equals").parameterCount(2);
  private static boolean isArrayEqualityComparison(PsiExpression expression) {
    return expression instanceof PsiMethodCallExpression && ARRAYS_EQUALS.test((PsiMethodCallExpression)expression);
  }

  private static boolean isInstanceOfMethodExistsWithMatchingParams(@NotNull AssertHint assertHint) {
    final PsiClass clazz = assertHint.getMethod().getContainingClass();
    if (clazz == null || !JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_ASSERTIONS.equals(clazz.getQualifiedName())) return false;
    final Module junitModule = ModuleUtilCore.findModuleForPsiElement(assertHint.getOriginalExpression());
    if (junitModule != null) {
      final String version = JavaLibraryUtil.getLibraryVersion(junitModule, "org.junit.jupiter:junit-jupiter-api");
      if (version != null) return VersionComparatorUtil.compare(version, "5.8") >= 0;
    }
    final PsiMethod[] methods = clazz.findMethodsByName("assertInstanceOf", true);
    final PsiParameterList originalParameters = assertHint.getMethod().getParameterList();
    for (final PsiMethod method : methods) {
      final PsiParameterList parameters = method.getParameterList();
      if (parameters.getParametersCount() - 1 != originalParameters.getParametersCount()) {
        continue; // assertTrue(condition, ?, ?, ...) vs assertInstanceOf(param1, param2, ?, ?, ...)
      }
      if (originalParameters.getParametersCount() == 1) return true;
      final PsiParameter[] originalParams = originalParameters.getParameters();
      final PsiParameter[] params = parameters.getParameters();

      if (IntStream.range(1, originalParams.length)
        .allMatch(i -> Objects.equals(originalParams[i].getType(), params[i + 1].getType()))) {
        return true;
      }
    }
    return false;
  }

  private static boolean isInstanceOfComparison(@NotNull PsiExpression expression) {
    return expression instanceof PsiInstanceOfExpression;
  }

  private static boolean isIdentityComparison(PsiExpression expression) {
    if (!(expression instanceof PsiBinaryExpression binaryExpression)) {
      return false;
    }
    if (!ComparisonUtils.isEqualityComparison(binaryExpression)) {
      return false;
    }
    final PsiExpression rhs = binaryExpression.getROperand();
    if (rhs == null) {
      return false;
    }
    final PsiExpression lhs = binaryExpression.getLOperand();
    final PsiType lhsType = lhs.getType();
    if (lhsType instanceof PsiPrimitiveType) {
      return false;
    }
    final PsiType rhsType = rhs.getType();
    return !(rhsType instanceof PsiPrimitiveType);
  }

  private static class SimplifyAssertFix extends PsiUpdateModCommandQuickFix {

    @Override
    public @NotNull String getFamilyName() {
      return InspectionGadgetsBundle.message("simplify.junit.assertion.simplify.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement methodNameIdentifier, @NotNull ModPsiUpdater updater) {
      final PsiElement parent = methodNameIdentifier.getParent();
      if (parent == null) {
        return;
      }
      final PsiMethodCallExpression callExpression = (PsiMethodCallExpression)parent.getParent();
      final AssertHint assertHint = AssertHint.createAssertEqualsHint(callExpression);
      if (assertHint != null && isAssertEqualsThatCouldBeAssertLiteral(assertHint)) {
        replaceAssertEqualsWithAssertLiteral(assertHint);
      }
      else {
        final AssertHint assertTrueFalseHint = AssertHint.createAssertTrueFalseHint(callExpression);
        if (assertTrueFalseHint == null) {
          return;
        }
        final boolean assertTrue = assertTrueFalseHint.isAssertTrue();
        final PsiExpression argument = assertTrueFalseHint.getFirstArgument();
        if (ComparisonUtils.isNullComparison(argument)) {
          replaceAssertWithAssertNull(assertTrueFalseHint);
        }
        else if (isIdentityComparison(argument)) {
          replaceWithAssertSame(assertTrueFalseHint);
        }
        else if (assertTrue && isEqualityComparison(argument)) {
          replaceWithAssertEquals(assertTrueFalseHint, "assertEquals");
        }
        else if (isAssertThatCouldBeFail(argument, !assertTrue)) {
          replaceWithFail(assertTrueFalseHint);
        }
        else if (isEqualityComparison(argument)) {
          replaceWithAssertEquals(assertTrueFalseHint, "assertNotEquals");
        }
        else if (assertTrue && isArrayEqualityComparison(argument)) {
          replaceWithAssertEquals(assertTrueFalseHint, "assertArrayEquals");
        }
        else if (BoolUtils.isNegation(argument)) {
          replaceWithNegatedBooleanAssertion(assertTrueFalseHint);
        }
        else if (assertTrue && isInstanceOfComparison(argument) && isInstanceOfMethodExistsWithMatchingParams(assertTrueFalseHint)) {
          replaceWithInstanceOfComparison(assertTrueFalseHint);
        }
      }
    }

    private static void addStaticImportOrQualifier(String methodName, AssertHint assertHint, StringBuilder out) {
      final PsiMethodCallExpression originalMethodCall = (PsiMethodCallExpression)assertHint.getOriginalExpression();
      final PsiReferenceExpression methodExpression = originalMethodCall.getMethodExpression();
      final PsiExpression qualifier = methodExpression.getQualifierExpression();
      if (qualifier == null) {
        final PsiMethod method = assertHint.getMethod();
        final PsiClass containingClass = method.getContainingClass();
        if (containingClass == null) {
          return;
        }
        final String className = containingClass.getQualifiedName();
        if (className == null) {
          return;
        }
        if (!ImportUtils.addStaticImport(className, methodName, originalMethodCall)) {
          // add qualifier if old call was to JUnit4 method and adding static import failed
          out.append(className).append(".");
        }
      }
      else {
        // apparently not statically imported, keep old qualifier in new assert call
        out.append(qualifier.getText()).append('.');
      }
    }

    private static void replaceWithFail(AssertHint assertHint) {
      final @NonNls StringBuilder newExpression = new StringBuilder();
      addStaticImportOrQualifier("fail", assertHint, newExpression);
      newExpression.append("fail(");
      final PsiExpression message = assertHint.getMessage();
      if (message != null) {
        newExpression.append(message.getText());
      }
      newExpression.append(')');
      PsiReplacementUtil.replaceExpressionAndShorten(assertHint.getOriginalExpression(), newExpression.toString());
    }

    /**
     * <code>assertTrue</code> -> <code>assertEquals</code>
     * <p/
     * <code>assertFalse</code> -> <code>assertNotEquals</code> (do not replace for junit 5 Assertions
     * as there is no primitive overloads for <code>assertNotEquals</code> and boxing would be enforced if replaced)
     */
    private static void replaceWithAssertEquals(AssertHint assertHint, final @NonNls String methodName) {
      final PsiExpression firstArgument = assertHint.getFirstArgument();
      PsiExpression lhs = null;
      PsiExpression rhs = null;
      if (firstArgument instanceof PsiBinaryExpression binaryExpression) {
        lhs = binaryExpression.getLOperand();
        rhs = binaryExpression.getROperand();
      }
      else {
        final EqualityCheck check = EqualityCheck.from(firstArgument);
        if (check != null) {
          lhs = check.getLeft();
          rhs = check.getRight();
        }
        else if (firstArgument instanceof PsiMethodCallExpression && ARRAYS_EQUALS.test((PsiMethodCallExpression)firstArgument)) {
          final PsiExpression[] args = ((PsiMethodCallExpression)firstArgument).getArgumentList().getExpressions();
          lhs = args[0];
          rhs = args[1];
        }
      }
      if (!ExpressionUtils.isEvaluatedAtCompileTime(lhs) && ExpressionUtils.isEvaluatedAtCompileTime(rhs)) {
        final PsiExpression temp = lhs;
        lhs = rhs;
        rhs = temp;
      }
      if (lhs == null || rhs == null) {
        return;
      }

      if (!assertHint.isExpectedActualOrder()) {
        final PsiExpression temp = lhs;
        lhs = rhs;
        rhs = temp;
      }

      final StringBuilder buf = new StringBuilder();
      final PsiType lhsType = lhs.getType();
      final PsiType rhsType = rhs.getType();
      if (lhsType != null && rhsType != null && PsiUtil.isLanguageLevel5OrHigher(lhs)) {
        final PsiPrimitiveType rhsUnboxedType = PsiPrimitiveType.getUnboxedType(rhsType);
        if (isPrimitiveAndBoxedWithOverloads(lhsType, rhsType) && rhsUnboxedType != null) {
          buf.append(lhs.getText()).append(",(").append(rhsUnboxedType.getCanonicalText()).append(')').append(rhs.getText());
        }
        else {
          final PsiPrimitiveType unboxedType = PsiPrimitiveType.getUnboxedType(lhsType);
          if (isPrimitiveAndBoxedWithOverloads(rhsType, lhsType) && unboxedType != null) {
            buf.append('(').append(unboxedType.getCanonicalText()).append(')').append(lhs.getText()).append(',').append(rhs.getText());
          }
          else {
            buf.append(lhs.getText()).append(',').append(rhs.getText());
          }
        }
      }
      else {
        buf.append(lhs.getText()).append(',').append(rhs.getText());
      }

      final PsiExpression originalExpression = assertHint.getOriginalExpression();
      if (lhsType != null && TypeConversionUtil.isFloatOrDoubleType(lhsType.getDeepComponentType()) ||
          rhsType != null && TypeConversionUtil.isFloatOrDoubleType(rhsType.getDeepComponentType())) {
        final String noDelta = compoundMethodCall(methodName, assertHint, buf.toString());
        final PsiElementFactory factory = JavaPsiFacade.getElementFactory(originalExpression.getProject());
        final PsiExpression expression = methodName.equals("assertNotEquals")
                                         ? null
                                         : factory.createExpressionFromText(noDelta, originalExpression);
        final PsiMethod method = expression instanceof PsiMethodCallExpression ? ((PsiMethodCallExpression)expression).resolveMethod() : null;
        if (method == null || method.isDeprecated()) {
          buf.append(",0.0");
        }
      }
      final String newExpression = compoundMethodCall(methodName, assertHint, buf.toString());
      PsiReplacementUtil.replaceExpressionAndShorten(originalExpression, newExpression);
    }

    private static boolean isPrimitiveAndBoxedWithOverloads(PsiType lhsType, PsiType rhsType) {
      if (lhsType instanceof PsiPrimitiveType && !PsiTypes.floatType().equals(lhsType) && !PsiTypes.doubleType().equals(lhsType)) {
        return rhsType instanceof PsiClassType;
      }
      return false;
    }

    private static void replaceWithNegatedBooleanAssertion(AssertHint assertHint) {
      final PsiPrefixExpression expression = (PsiPrefixExpression)assertHint.getFirstArgument();
      final PsiExpression operand = PsiUtil.skipParenthesizedExprDown(expression.getOperand());
      if (operand == null) {
        return;
      }
      final String newMethodName = assertHint.isAssertTrue() ? "assertFalse" : "assertTrue";
      final String newExpression = compoundMethodCall(newMethodName, assertHint, operand.getText());
      PsiReplacementUtil.replaceExpressionAndShorten(assertHint.getOriginalExpression(), newExpression);
    }

    private static void replaceAssertWithAssertNull(AssertHint assertHint) {
      final PsiBinaryExpression binaryExpression = (PsiBinaryExpression)assertHint.getFirstArgument();
      final PsiExpression lhs = binaryExpression.getLOperand();
      PsiExpression rhs = binaryExpression.getROperand();
      if (rhs == null) {
        return;
      }
      final IElementType tokenType = binaryExpression.getOperationTokenType();
      if (!ExpressionUtils.isEvaluatedAtCompileTime(lhs) && ExpressionUtils.isEvaluatedAtCompileTime(rhs)) {
        rhs = lhs;
      }
      final @NonNls String methodName = assertHint.getMethod().getName();
      final @NonNls String memberName;
      if ("assertFalse".equals(methodName) ^ tokenType.equals(JavaTokenType.NE)) {
        memberName = "assertNotNull";
      }
      else {
        memberName = "assertNull";
      }
      final String newExpression = compoundMethodCall(memberName, assertHint, rhs.getText());
      PsiReplacementUtil.replaceExpressionAndShorten(assertHint.getOriginalExpression(), newExpression);
    }

    private static void replaceWithInstanceOfComparison(@NotNull AssertHint assertHint) {
      final PsiInstanceOfExpression expression = (PsiInstanceOfExpression)assertHint.getFirstArgument();
      final PsiExpression operand = expression.getOperand();
      final PsiTypeElement type = expression.getCheckType();
      if (type == null) return;
      final String newExpression = compoundMethodCall("assertInstanceOf", assertHint, type.getText() + ".class," + operand.getText());
      PsiReplacementUtil.replaceExpressionAndShorten(assertHint.getOriginalExpression(), newExpression);
    }

    private static String compoundMethodCall(@NonNls String methodName, AssertHint assertHint, String args) {
      final PsiExpression message = assertHint.getMessage();
      final StringBuilder newExpression = new StringBuilder();
      addStaticImportOrQualifier(methodName, assertHint, newExpression);
      newExpression.append(methodName).append('(');
      final int index = assertHint.getArgIndex();
      if (message != null && index != 0) {
        newExpression.append(message.getText()).append(',');
      }
      newExpression.append(args);
      if (message != null && index == 0) {
        newExpression.append(',').append(message.getText());
      }
      newExpression.append(')');
      return newExpression.toString();
    }

    private static void replaceWithAssertSame(AssertHint assertHint) {
      final PsiBinaryExpression firstArgument = (PsiBinaryExpression)assertHint.getFirstArgument();
      PsiExpression lhs = firstArgument.getLOperand();
      PsiExpression rhs = firstArgument.getROperand();
      final IElementType tokenType = firstArgument.getOperationTokenType();
      if (!ExpressionUtils.isEvaluatedAtCompileTime(lhs) && ExpressionUtils.isEvaluatedAtCompileTime(rhs)) {
        final PsiExpression temp = lhs;
        lhs = rhs;
        rhs = temp;
      }
      if (rhs == null) {
        return;
      }
      final @NonNls String methodName = assertHint.getMethod().getName();
      final @NonNls String memberName;
      if ("assertFalse".equals(methodName) ^ tokenType.equals(JavaTokenType.NE)) {
        memberName = "assertNotSame";
      }
      else {
        memberName = "assertSame";
      }
      final String newExpression = compoundMethodCall(memberName, assertHint, lhs.getText() + "," + rhs.getText());
      PsiReplacementUtil.replaceExpressionAndShorten(assertHint.getOriginalExpression(), newExpression);
    }

    private static void replaceAssertEqualsWithAssertLiteral(AssertHint assertHint) {
      final PsiExpression firstTestArgument = assertHint.getFirstArgument();
      final PsiExpression secondTestArgument = assertHint.getSecondArgument();
      final String literalValue;
      final String compareValue;
      if (isSimpleLiteral(firstTestArgument, secondTestArgument)) {
        literalValue = firstTestArgument.getText();
        compareValue = secondTestArgument.getText();
      }
      else {
        literalValue = secondTestArgument.getText();
        compareValue = firstTestArgument.getText();
      }
      final String uppercaseLiteralValue = Character.toUpperCase(literalValue.charAt(0)) + literalValue.substring(1);
      final @NonNls String methodName = "assert" + uppercaseLiteralValue;
      final String newExpression = compoundMethodCall(methodName, assertHint, compareValue);
      PsiReplacementUtil.replaceExpressionAndShorten(assertHint.getOriginalExpression(), newExpression);
    }
  }

  private static class SimplifiableJUnitAssertionVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final AssertHint assertHint = AssertHint.createAssertEqualsHint(expression);
      if (assertHint != null && isAssertEqualsThatCouldBeAssertLiteral(assertHint)) {
        registerMethodCallError(expression, getReplacementMethodName(assertHint));
      }
      else {
        final AssertHint assertTrueFalseHint = AssertHint.createAssertTrueFalseHint(expression);
        if (assertTrueFalseHint == null) {
          return;
        }

        final boolean assertTrue = assertTrueFalseHint.isAssertTrue();
        final PsiExpression firstArgument = assertTrueFalseHint.getFirstArgument();
        if (ComparisonUtils.isNullComparison(firstArgument)) {
          registerMethodCallError(expression, assertTrue == isEqEqExpression(firstArgument) ? "assertNull()" : "assertNotNull()");
        }
        else if (isIdentityComparison(firstArgument)) {
          registerMethodCallError(expression, assertTrue == isEqEqExpression(firstArgument) ? "assertSame()" : "assertNotSame()");
        }
        else {
          if (isEqualityComparison(firstArgument)) {
            if (assertTrue) {
              registerMethodCallError(expression, "assertEquals()");
            }
            else if (firstArgument instanceof PsiMethodCallExpression || hasPrimitiveOverload(assertTrueFalseHint)) {
              registerMethodCallError(expression, "assertNotEquals()");
            }
          }
          else if (isAssertThatCouldBeFail(firstArgument, !assertTrue)) {
            registerMethodCallError(expression, "fail()");
          }
          else if (assertTrue && assertTrueFalseHint.isExpectedActualOrder() && isArrayEqualityComparison(firstArgument)) {
            registerMethodCallError(expression, "assertArrayEquals()");
          }
          else if (BoolUtils.isNegation(firstArgument)) {
            registerMethodCallError(expression, assertTrue ? "assertFalse()" : "assertTrue()");
          }
          else if (assertTrue && isInstanceOfComparison(firstArgument) && isInstanceOfMethodExistsWithMatchingParams(assertTrueFalseHint)) {
            registerMethodCallError(expression, "assertInstanceOf()");
          }
        }
      }
    }

    private static boolean hasPrimitiveOverload(AssertHint assertHint) {
      final PsiClass containingClass = assertHint.getMethod().getContainingClass();
      if (containingClass == null) {
        return false;
      }
      final PsiMethod primitiveOverload = CachedValuesManager.getCachedValue(containingClass, () -> {
        final PsiMethod patternMethod = JavaPsiFacade.getElementFactory(containingClass.getProject())
          .createMethodFromText("public static void assertNotEquals(long a, long b){}", containingClass);
        return new CachedValueProvider.Result<>(containingClass.findMethodBySignature(patternMethod, true),
                                                PsiModificationTracker.MODIFICATION_COUNT);
      });
      return primitiveOverload != null;
    }

    private static @NonNls String getReplacementMethodName(AssertHint assertHint) {
      final PsiExpression firstArgument = assertHint.getFirstArgument();
      final PsiExpression secondArgument = assertHint.getSecondArgument();
      final PsiLiteralExpression literalExpression;
      if (firstArgument instanceof PsiLiteralExpression) {
        literalExpression = (PsiLiteralExpression)firstArgument;
      }
      else if (secondArgument instanceof PsiLiteralExpression) {
        literalExpression = (PsiLiteralExpression)secondArgument;
      }
      else {
        return "";
      }
      final Object value = literalExpression.getValue();
      if (value == Boolean.TRUE) {
        return "assertTrue()";
      }
      else if (value == Boolean.FALSE) {
        return "assertFalse()";
      }
      else if (value == null) {
        return "assertNull()";
      }
      return "";
    }

    private static boolean isEqEqExpression(PsiExpression argument) {
      if (!(argument instanceof PsiBinaryExpression binaryExpression)) {
        return false;
      }
      final IElementType tokenType = binaryExpression.getOperationTokenType();
      return JavaTokenType.EQEQ.equals(tokenType);
    }
  }
}
