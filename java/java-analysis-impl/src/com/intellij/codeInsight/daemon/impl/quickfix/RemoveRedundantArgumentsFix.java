// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.analysis.JavaHighlightUtil;
import com.intellij.codeInsight.intention.CommonIntentionAction;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.function.Consumer;

public final class RemoveRedundantArgumentsFix extends PsiUpdateModCommandAction<PsiExpressionList> {
  private final PsiMethod myTargetMethod;
  private final PsiSubstitutor mySubstitutor;

  private RemoveRedundantArgumentsFix(@NotNull PsiMethod targetMethod,
                                      @NotNull PsiExpressionList arguments,
                                      @NotNull PsiSubstitutor substitutor) {
    super(arguments);
    myTargetMethod = targetMethod;
    mySubstitutor = substitutor;
  }

  @Override
  public @NotNull String getFamilyName() {
    return QuickFixBundle.message("remove.redundant.arguments.family");
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiExpressionList args) {
    if (!myTargetMethod.isValid() || myTargetMethod.getContainingClass() == null) return null;
    if (!mySubstitutor.isValid()) return null;
    if (findRedundantArgument(args.getExpressions(), myTargetMethod.getParameterList().getParameters(), mySubstitutor) == null) {
      return null;
    }
    return Presentation.of(QuickFixBundle.message("remove.redundant.arguments.text", JavaHighlightUtil.formatMethod(myTargetMethod)));
  }

  private static PsiExpression @Nullable [] findRedundantArgument(PsiExpression @NotNull [] arguments,
                                                                  PsiParameter @NotNull [] parameters,
                                                                  @NotNull PsiSubstitutor substitutor) {
    if (arguments.length <= parameters.length) return null;

    for (int i = 0; i < parameters.length; i++) {
      final PsiExpression argument = arguments[i];
      final PsiParameter parameter = parameters[i];

      final PsiType argumentType = argument.getType();
      if (argumentType == null) return null;
      final PsiType parameterType = substitutor.substitute(parameter.getType());

      if (!TypeConversionUtil.isAssignable(parameterType, argumentType)) {
        return null;
      }
    }

    return Arrays.copyOfRange(arguments, parameters.length, arguments.length);
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiExpressionList args, @NotNull ModPsiUpdater updater) {
    final PsiExpression[] redundantArguments = findRedundantArgument(args.getExpressions(), 
                                                                     myTargetMethod.getParameterList().getParameters(), mySubstitutor);
    if (redundantArguments != null) {
      for (PsiExpression argument : redundantArguments) {
        argument.delete();
      }
    }
  }

  public static void registerIntentions(JavaResolveResult @NotNull [] candidates,
                                        @NotNull PsiExpressionList arguments,
                                        @NotNull Consumer<? super CommonIntentionAction> info) {
    for (JavaResolveResult candidate : candidates) {
      registerIntention(arguments, info, candidate);
    }
  }

  public static void registerIntentions(@NotNull PsiExpressionList arguments,
                                        @NotNull Consumer<? super CommonIntentionAction> info) {
    if (!arguments.isEmpty()) {
      info.accept(new ForImplicitConstructorAction(arguments));
    }
  }

  private static void registerIntention(@NotNull PsiExpressionList arguments,
                                        @NotNull Consumer<? super CommonIntentionAction> info,
                                        @NotNull JavaResolveResult candidate) {
    if (!candidate.isStaticsScopeCorrect()) return;
    PsiMethod method = (PsiMethod)candidate.getElement();
    PsiSubstitutor substitutor = candidate.getSubstitutor();
    if (method == null || !BaseIntentionAction.canModify(arguments)) return;
    if (method.isConstructor() && arguments.getParent() instanceof PsiMethodCallExpression &&
        ((PsiMethodCallExpression)arguments.getParent()).getMethodExpression().textMatches("this") &&
        PsiTreeUtil.isAncestor(method, arguments, true)) {
      // Avoid creating recursive constructor call
      return;
    }
    info.accept(new RemoveRedundantArgumentsFix(method, arguments, substitutor));
  }

  private static class ForImplicitConstructorAction extends PsiUpdateModCommandAction<PsiExpressionList> {
    ForImplicitConstructorAction(@NotNull PsiExpressionList list) { 
      super(list);
    }

    @Override
    public @NotNull String getFamilyName() {
      return QuickFixBundle.message("remove.redundant.arguments.family");
    }

    @Override
    protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiExpressionList args) {
      return args.isEmpty() ? null : Presentation.of(getFamilyName());
    }

    @Override
    protected void invoke(@NotNull ActionContext context, @NotNull PsiExpressionList args, @NotNull ModPsiUpdater updater) {
      PsiExpression[] expressions = args.getExpressions();
      if (expressions.length == 0) return;
      args.deleteChildRange(expressions[0], expressions[expressions.length - 1]);
    }
  }
}
