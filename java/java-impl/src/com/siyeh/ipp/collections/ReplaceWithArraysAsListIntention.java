// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ipp.collections;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.dataFlow.NullabilityUtil;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.Presentation;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ipp.base.MCIntention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public final class ReplaceWithArraysAsListIntention extends MCIntention {
  private String replacementText = null;

  @Override
  public @NotNull String getFamilyName() {
    return IntentionPowerPackBundle.message("replace.with.arrays.as.list.intention.family.name");
  }

  @Override
  public @IntentionName @NotNull String getTextForElement(@NotNull PsiElement element) {
    return CommonQuickFixBundle.message("fix.replace.with.x", replacementText + "()");
  }

  @Override
  public @Nullable Presentation getPresentation(@NotNull ActionContext context) {
    Presentation presentation = super.getPresentation(context);
    return presentation == null ? null : presentation.withPriority(PriorityAction.Priority.HIGH);
  }

  @NotNull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return e -> {
      if (!(e instanceof PsiMethodCallExpression methodCallExpression)) {
        return false;
      }
      final PsiMethod method = methodCallExpression.resolveMethod();
      if (method == null) {
        return false;
      }
      final PsiClass aClass = method.getContainingClass();
      if (aClass == null) {
        return false;
      }
      final String qualifiedName = aClass.getQualifiedName();
      if (qualifiedName == null || !qualifiedName.equals("java.util.Collections")) {
        return false;
      }
      final String name = method.getName();
      return (replacementText = getReplacementMethodText(name, methodCallExpression)) != null;
    };
  }

  @Override
  protected void processIntention(@NotNull PsiElement element) {
    final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)element;
    final PsiExpressionList argumentList = methodCallExpression.getArgumentList();
    final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
    final PsiReferenceParameterList parameterList = methodExpression.getParameterList();
    CommentTracker commentTracker = new CommentTracker();
    if (parameterList != null) {
      final int dotIndex = replacementText.lastIndexOf('.') + 1;
      replacementText = replacementText.substring(0, dotIndex) + commentTracker.text(parameterList) + replacementText.substring(dotIndex);
    }
    PsiReplacementUtil.replaceExpressionAndShorten(methodCallExpression, replacementText + commentTracker.text(argumentList), commentTracker);
  }

  private static String getReplacementMethodText(String methodName, PsiMethodCallExpression context) {
    final PsiExpression[] arguments = context.getArgumentList().getExpressions();
    if (methodName.equals("emptyList") && arguments.length == 1 &&
        !PsiUtil.isLanguageLevel9OrHigher(context) && ClassUtils.findClass("com.google.common.collect.ImmutableList", context) == null) {
      return "java.util.Collections.singletonList";
    }
    if (methodName.equals("emptyList") || methodName.equals("singletonList")) {
      if (!ContainerUtil.exists(arguments, ReplaceWithArraysAsListIntention::isPossiblyNull)) {
        if (PsiUtil.isLanguageLevel9OrHigher(context)) {
          return "java.util.List.of";
        }
        else if (ClassUtils.findClass("com.google.common.collect.ImmutableList", context) != null) {
          return "com.google.common.collect.ImmutableList.of";
        }
      }
      return "java.util.Arrays.asList";
    }
    if (methodName.equals("emptySet") || methodName.equals("singleton")) {
      if (PsiUtil.isLanguageLevel9OrHigher(context)) {
        return "java.util.Set.of";
      }
      else if (ClassUtils.findClass("com.google.common.collect.ImmutableSet", context) != null) {
        return "com.google.common.collect.ImmutableSet.of";
      }
    }
    else if (methodName.equals("emptyMap") || methodName.equals("singletonMap")) {
      if (PsiUtil.isLanguageLevel9OrHigher(context)) {
        return "java.util.Map.of";
      }
      else if (ClassUtils.findClass("com.google.common.collect.ImmutableMap", context) != null) {
        return "com.google.common.collect.ImmutableMap.of";
      }
    }
    return null;
  }

  private static boolean isPossiblyNull(PsiExpression expression) {
    return NullabilityUtil.getExpressionNullability(expression) == Nullability.NULLABLE;
  }
}
