// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.style;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.dataFlow.NullabilityUtil;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.graphInference.PsiPolyExpressionUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Objects;

import static com.intellij.psi.CommonClassNames.*;
import static com.siyeh.ig.callMatcher.CallMatcher.staticCall;

public final class UnnecessaryCallToStringValueOfInspection extends BaseInspection implements CleanupLocalInspectionTool {
  private static final @NonNls CallMatcher STATIC_TO_STRING_CONVERTERS = CallMatcher.anyOf(
    staticCall(JAVA_LANG_STRING, "valueOf").parameterTypes("boolean"),
    staticCall(JAVA_LANG_STRING, "valueOf").parameterTypes("char"),
    staticCall(JAVA_LANG_STRING, "valueOf").parameterTypes("double"),
    staticCall(JAVA_LANG_STRING, "valueOf").parameterTypes("float"),
    staticCall(JAVA_LANG_STRING, "valueOf").parameterTypes("int"),
    staticCall(JAVA_LANG_STRING, "valueOf").parameterTypes("long"),
    staticCall(JAVA_LANG_STRING, "valueOf").parameterTypes(JAVA_LANG_OBJECT),
    staticCall(JAVA_LANG_BOOLEAN, "toString").parameterTypes("boolean"),
    staticCall(JAVA_LANG_BYTE, "toString").parameterTypes("byte"),
    staticCall(JAVA_LANG_SHORT, "toString").parameterTypes("short"),
    staticCall(JAVA_LANG_CHARACTER, "toString").parameterTypes("char"),
    staticCall(JAVA_LANG_INTEGER, "toString").parameterTypes("int"),
    staticCall(JAVA_LANG_LONG, "toString").parameterTypes("long"),
    staticCall(JAVA_LANG_FLOAT, "toString").parameterTypes("float"),
    staticCall(JAVA_LANG_DOUBLE, "toString").parameterTypes("double"),
    staticCall(JAVA_UTIL_OBJECTS, "toString").parameterTypes(JAVA_LANG_OBJECT)
  );

  public boolean reportWithEmptyString = false;

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    if (infos[1] == Boolean.TRUE) {
      return InspectionGadgetsBundle.message("unnecessary.tostring.call.problem.empty.string.descriptor");
    }
    return InspectionGadgetsBundle.message("unnecessary.tostring.call.problem.descriptor");
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return OptPane.pane(
      OptPane.checkbox("reportWithEmptyString", InspectionGadgetsBundle.message("unnecessary.tostring.call.option.report.with.empty.string"))
    );
  }

  @Override
  @Nullable
  protected LocalQuickFix buildFix(Object... infos) {
    final String text = (String)infos[0];
    final Boolean useEmptyString = (Boolean)infos[1];
    return new UnnecessaryCallToStringValueOfFix(text, useEmptyString);
  }

  private static String calculateReplacementText(@NotNull PsiMethodCallExpression call, PsiExpression expression, boolean withEmptyString) {
    if (withEmptyString) {
      String text = ParenthesesUtils.getText(expression, ParenthesesUtils.ADDITIVE_PRECEDENCE);
      text = "\"\" + " + text;
      return text;
    }

    if (!(expression instanceof PsiPolyadicExpression)) {
      return expression.getText();
    }
    PsiPolyadicExpression parentCall = ObjectUtils.tryCast(ParenthesesUtils.getParentSkipParentheses(call), PsiPolyadicExpression.class);
    if (parentCall == null) {
      return expression.getText();
    }
    final PsiType type = expression.getType();
    int precedence = ParenthesesUtils.getPrecedence(expression);
    if (TypeUtils.typeEquals(JAVA_LANG_STRING, type) || precedence < ParenthesesUtils.ADDITIVE_PRECEDENCE ||
        (precedence == ParenthesesUtils.ADDITIVE_PRECEDENCE && ArrayUtil.getFirstElement(parentCall.getOperands()) == call)) {
      return expression.getText();
    }
    return '(' + expression.getText() + ')';
  }

  private static class UnnecessaryCallToStringValueOfFix extends PsiUpdateModCommandQuickFix {

    private final String replacementText;
    private final boolean useEmptyString;

    UnnecessaryCallToStringValueOfFix(String replacementText, boolean useEmptyString) {
      this.replacementText = replacementText;
      this.useEmptyString = useEmptyString;
    }

    @Override
    @NotNull
    public String getName() {
      return CommonQuickFixBundle.message("fix.replace.with.x", replacementText);
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return CommonQuickFixBundle.message("fix.simplify");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement startElement, @NotNull ModPsiUpdater updater) {
      final PsiMethodCallExpression call = ObjectUtils.tryCast(startElement, PsiMethodCallExpression.class);
      if (call == null) return;
      PsiExpression arg = getArgument(call);
      if (arg == null) return;
      CommentTracker tracker = new CommentTracker();
      boolean couldBeUnwrappedRedundantConversion = couldBeUnwrappedRedundantConversion(arg, call);
      if (!couldBeUnwrappedRedundantConversion && !useEmptyString) {
        return;
      }
      PsiReplacementUtil.replaceExpression(call, calculateReplacementText(call, tracker.markUnchanged(arg),
                                                                          !couldBeUnwrappedRedundantConversion), tracker);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UnnecessaryCallToStringValueOfVisitor();
  }

  private class UnnecessaryCallToStringValueOfVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
      PsiExpression argument = getArgument(call);
      if (argument == null) return;
      if (!couldBeUnwrappedRedundantConversion(argument, call)) {
        ProblemHighlightType highlightType = reportWithEmptyString ? ProblemHighlightType.WEAK_WARNING : ProblemHighlightType.INFORMATION;
        if (highlightType == ProblemHighlightType.INFORMATION && !isOnTheFly()) {
          return;
        }
        registerErrorAtOffset(call, 0, call.getArgumentList().getStartOffsetInParent(), highlightType,
                              calculateReplacementText(call, argument, true), Boolean.TRUE);
        return;
      }

      registerErrorAtOffset(call, 0, call.getArgumentList().getStartOffsetInParent(),
                            calculateReplacementText(call, argument, false), Boolean.FALSE);
    }
  }

  @Nullable
  private static PsiExpression getArgument(@NotNull PsiMethodCallExpression call) {
    if (!STATIC_TO_STRING_CONVERTERS.test(call)) return null;
    return PsiUtil.skipParenthesizedExprDown(call.getArgumentList().getExpressions()[0]);
  }

  private static boolean couldBeUnwrappedRedundantConversion(@NotNull PsiExpression argument,
                                                             @NotNull PsiMethodCallExpression call) {
    PsiType argumentType = argument.getType();
    if (argumentType instanceof PsiPrimitiveType) {
      PsiMethod method = call.resolveMethod();
      assert method != null; // otherwise the matcher above won't match
      if (!Objects.requireNonNull(method.getParameterList().getParameter(0)).getType().equals(argumentType)) {
        return false;
      }
    }
    final boolean throwable = TypeUtils.expressionHasTypeOrSubtype(argument, JAVA_LANG_THROWABLE);
    if (ExpressionUtils.isConversionToStringNecessary(call, throwable)) {
      if (!TypeUtils.isJavaLangString(argumentType) ||
          NullabilityUtil.getExpressionNullability(argument, true) != Nullability.NOT_NULL) {
        return false;
      }
    }
    return !isReplacementAmbiguous(call, argument);
  }

  private static boolean isReplacementAmbiguous(PsiMethodCallExpression call, PsiExpression argument) {
    if (!PsiPolyExpressionUtil.isPolyExpression(argument)) return false;
    PsiExpressionList exprList = ObjectUtils.tryCast(ParenthesesUtils.getParentSkipParentheses(call), PsiExpressionList.class);
    if (exprList == null) return false;
    PsiCallExpression parentCall = ObjectUtils.tryCast(exprList.getParent(), PsiCallExpression.class);
    if (parentCall == null) return false;
    PsiCallExpression copy = (PsiCallExpression)parentCall.copy();
    int argIndex = ContainerUtil.indexOf(Arrays.asList(exprList.getExpressions()), expr -> PsiUtil.skipParenthesizedExprDown(expr) == call);
    assert argIndex >= -1;
    PsiExpression argCopy = Objects.requireNonNull(copy.getArgumentList()).getExpressions()[argIndex];
    argCopy.replace(argument);
    JavaResolveResult result = copy.resolveMethodGenerics();
    return !result.isValidResult();
  }
}
