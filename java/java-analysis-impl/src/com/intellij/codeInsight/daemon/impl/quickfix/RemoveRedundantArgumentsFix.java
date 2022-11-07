// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.analysis.JavaHighlightUtil;
import com.intellij.codeInsight.intention.FileModifier;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * @author Danila Ponomarenko
 */
public final class RemoveRedundantArgumentsFix implements IntentionAction {
  private final PsiMethod myTargetMethod;
  private final PsiExpression[] myArguments;
  private final PsiSubstitutor mySubstitutor;

  private RemoveRedundantArgumentsFix(@NotNull PsiMethod targetMethod,
                                      PsiExpression @NotNull [] arguments,
                                      @NotNull PsiSubstitutor substitutor) {
    myTargetMethod = targetMethod;
    myArguments = arguments;
    mySubstitutor = substitutor;
  }

  @NotNull
  @Override
  public String getText() {
    return QuickFixBundle.message("remove.redundant.arguments.text", JavaHighlightUtil.formatMethod(myTargetMethod));
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return QuickFixBundle.message("remove.redundant.arguments.family");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (!myTargetMethod.isValid() || myTargetMethod.getContainingClass() == null) return false;
    for (PsiExpression expression : myArguments) {
      if (!expression.isValid()) return false;
    }
    if (!mySubstitutor.isValid()) return false;

    return findRedundantArgument(myArguments, myTargetMethod.getParameterList().getParameters(), mySubstitutor) != null;
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
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final PsiExpression[] redundantArguments = findRedundantArgument(myArguments, myTargetMethod.getParameterList().getParameters(), mySubstitutor);
    if (redundantArguments != null) {
      for (PsiExpression argument : redundantArguments) {
        argument.delete();
      }
    }
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }

  public static void registerIntentions(JavaResolveResult @NotNull [] candidates,
                                        @NotNull PsiExpressionList arguments,
                                        @NotNull HighlightInfo.Builder highlightInfo,
                                        TextRange fixRange) {
    for (JavaResolveResult candidate : candidates) {
      registerIntention(arguments, highlightInfo, fixRange, candidate);
    }
  }

  public static void registerIntentions(@NotNull PsiExpressionList arguments,
                                        @NotNull HighlightInfo.Builder highlightInfo,
                                        TextRange fixRange) {
    if (!arguments.isEmpty()) {
      highlightInfo.registerFix(new ForImplicitConstructorAction(arguments), null, null, fixRange, null);
    }
  }

  private static void registerIntention(@NotNull PsiExpressionList arguments,
                                        @NotNull HighlightInfo.Builder builder,
                                        TextRange fixRange,
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
    IntentionAction action = new RemoveRedundantArgumentsFix(method, arguments.getExpressions(), substitutor);
    builder.registerFix(action, null, null, fixRange, null);
  }

  @Override
  public @NotNull FileModifier getFileModifierForPreview(@NotNull PsiFile target) {
    return new RemoveRedundantArgumentsFix(
      myTargetMethod,
      ContainerUtil.map2Array(myArguments, PsiExpression.class, arg -> PsiTreeUtil.findSameElementInCopy(arg, target)),
      mySubstitutor);
  }

  private static class ForImplicitConstructorAction implements IntentionAction {
    private final @NotNull PsiExpressionList myList;

    ForImplicitConstructorAction(@NotNull PsiExpressionList list) { 
      myList = list;
    }

    @Override
    public @Nullable FileModifier getFileModifierForPreview(@NotNull PsiFile target) {
      return new ForImplicitConstructorAction(PsiTreeUtil.findSameElementInCopy(myList, target));
    }

    @Override
    public @NotNull String getText() {
      return getFamilyName();
    }

    @Override
    public @NotNull String getFamilyName() {
      return QuickFixBundle.message("remove.redundant.arguments.family");
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
      return true;
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
      PsiExpression[] expressions = myList.getExpressions();
      myList.deleteChildRange(expressions[0], expressions[expressions.length - 1]);
    }

    @Override
    public boolean startInWriteAction() {
      return true;
    }
  }
}
