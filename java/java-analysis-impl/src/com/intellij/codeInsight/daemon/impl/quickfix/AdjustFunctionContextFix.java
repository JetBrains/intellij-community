// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.callMatcher.CallMapper;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.StreamApiUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

public class AdjustFunctionContextFix extends PsiUpdateModCommandAction<PsiMethodCallExpression> {
  private static final Function<PsiMethodCallExpression, Function<PsiType, String>>
    MAP_NAME_ADJUSTER = (PsiMethodCallExpression call) -> (PsiType type) -> {
    PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
    if (qualifier != null) {
      PsiType inType = StreamApiUtil.getStreamElementType(qualifier.getType());
      if (type.equals(inType)) return "map";
    }
    if (PsiTypes.intType().equals(type)) return "mapToInt";
    if (PsiTypes.longType().equals(type)) return "mapToLong";
    if (PsiTypes.doubleType().equals(type)) return "mapToDouble";
    return "mapToObj";
  };
  private static final Function<PsiType, String> FLAT_MAP_NAME_ADJUSTER = type -> {
    if(InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_UTIL_STREAM_INT_STREAM)) return "flatMapToInt";
    if(InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_UTIL_STREAM_LONG_STREAM)) return "flatMapToLong";
    if(InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_UTIL_STREAM_DOUBLE_STREAM)) return "flatMapToDouble";
    if(InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_UTIL_STREAM_STREAM)) return "flatMap";
    return null;
  };

  private static final CallMapper<Function<PsiType, String>> METHOD_NAME_ADJUSTER = new CallMapper<Function<PsiType, String>>()
    .register(
      CallMatcher.anyOf(
        CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_STREAM_INT_STREAM, "map", "mapToLong", "mapToDouble"),
        CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_STREAM_LONG_STREAM, "map", "mapToInt", "mapToDouble"),
        CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_STREAM_DOUBLE_STREAM, "map", "mapToInt", "mapToLong")
      ), MAP_NAME_ADJUSTER)
    .register(
      CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_STREAM_STREAM, "flatMap", "flatMapToInt", "flatMapToLong", "flatMapToDouble"),
      FLAT_MAP_NAME_ADJUSTER);

  private final String myOriginalName;
  private final String myNewName;

  private AdjustFunctionContextFix(@NotNull PsiMethodCallExpression call, @NotNull String targetMethodName) {
    super(call);
    myOriginalName = call.getMethodExpression().getReferenceName();
    myNewName = targetMethodName;
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiMethodCallExpression call, @NotNull ModPsiUpdater updater) {
    ExpressionUtils.bindCallTo(call, myNewName);
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiMethodCallExpression element) {
    return Presentation.of(QuickFixBundle.message("adjust.method.accepting.functional.expression.fix.text", myOriginalName, myNewName)).withPriority(
      PriorityAction.Priority.HIGH).withFixAllOption(this);
  }

  @Override
  public @Nls @NotNull String getFamilyName() {
    return QuickFixBundle.message("adjust.method.accepting.functional.expression.fix.family.name");
  }

  public static @Nullable IntentionAction createFix(@NotNull PsiExpression expression) {
    PsiFunctionalExpression fn = PsiTreeUtil.getParentOfType(expression, PsiFunctionalExpression.class, false);
    if (fn == null) return null;
    PsiType actualReturnType;
    if(expression instanceof PsiMethodReferenceExpression methodRef) {
      actualReturnType = PsiMethodReferenceUtil.getMethodReferenceReturnType(methodRef);
    } else {
      actualReturnType = expression.getType();
    }
    return createFix(actualReturnType, fn);
  }

  /**
   * @param actualReturnType actual (unexpected) return type of functional expression
   * @param fn functional expression
   * @return a fix that aims to adjust the surroundings
   */
  public static @Nullable IntentionAction createFix(@Nullable PsiType actualReturnType, @NotNull PsiFunctionalExpression fn) {
    PsiExpressionList expressionList = ObjectUtils.tryCast(fn.getParent(), PsiExpressionList.class);
    if (expressionList == null || expressionList.getExpressionCount() != 1) return null;
    PsiMethodCallExpression call = ObjectUtils.tryCast(expressionList.getParent(), PsiMethodCallExpression.class);
    Function<PsiType, String> remapper = METHOD_NAME_ADJUSTER.mapFirst(call);
    if (remapper == null) return null;
    String targetMethodName = remapper.apply(actualReturnType);
    if (targetMethodName == null) return null;
    return new AdjustFunctionContextFix(call, targetMethodName).asIntention();
  }
}
