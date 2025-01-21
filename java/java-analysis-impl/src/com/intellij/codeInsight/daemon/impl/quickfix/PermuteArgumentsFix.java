// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.CommonIntentionAction;
import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public final class PermuteArgumentsFix extends PsiUpdateModCommandAction<PsiCall> {
  private static final Logger LOG = Logger.getInstance(PermuteArgumentsFix.class);
  private final PsiCall myPermutation;

  private PermuteArgumentsFix(@NotNull PsiCall call, @NotNull PsiCall permutation) {
    super(call);
    myPermutation = permutation;
  }

  @Override
  public @NotNull String getFamilyName() {
    return QuickFixBundle.message("permute.arguments");
  }

  @Override
  protected @NotNull Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiCall element) {
    return Presentation.of(getFamilyName()).withPriority(PriorityAction.Priority.HIGH);
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiCall call, @NotNull ModPsiUpdater updater) {
    Objects.requireNonNull(call.getArgumentList()).replace(Objects.requireNonNull(myPermutation.getArgumentList()));
  }

  public static boolean registerFix(@NotNull Consumer<? super CommonIntentionAction> info, PsiCall callExpression, final CandidateInfo[] candidates) {
    PsiExpression[] expressions = Objects.requireNonNull(callExpression.getArgumentList()).getExpressions();
    if (expressions.length < 2) return false;
    List<PsiCall> permutations = new ArrayList<>();

    for (CandidateInfo candidate : candidates) {
      if (candidate instanceof MethodCandidateInfo methodCandidate) {
        PsiMethod method = methodCandidate.getElement();
        PsiSubstitutor substitutor = methodCandidate.getSubstitutor();

        PsiParameter[] parameters = method.getParameterList().getParameters();
        if (expressions.length != parameters.length) continue;
        int minIncompatibleIndex = parameters.length;
        int maxIncompatibleIndex = 0;
        int incompatibilitiesCount = 0;
        for (int i = 0; i < parameters.length; i++) {
          PsiParameter parameter = parameters[i];
          PsiType type = substitutor.substitute(parameter.getType());
          if (TypeConversionUtil.areTypesAssignmentCompatible(type, expressions[i])) continue;
          if (minIncompatibleIndex == parameters.length) minIncompatibleIndex = i;
          maxIncompatibleIndex = i;
          incompatibilitiesCount++;
        }

        try {
          PsiExpression[] clonedExpressions = expressions.clone();
          registerSwapFixes(clonedExpressions, callExpression, permutations, methodCandidate, incompatibilitiesCount, minIncompatibleIndex, maxIncompatibleIndex);
          registerShiftFixes(clonedExpressions, callExpression, permutations, methodCandidate, minIncompatibleIndex, maxIncompatibleIndex);
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
    }
    if (permutations.size() == 1) {
      PermuteArgumentsFix fix = new PermuteArgumentsFix(callExpression, permutations.get(0));
      info.accept(fix);
      return true;
    }

    return false;
  }

  private static void registerShiftFixes(final PsiExpression[] expressions, final PsiCall callExpression, final List<? super PsiCall> permutations,
                                         final MethodCandidateInfo methodCandidate, final int minIncompatibleIndex, final int maxIncompatibleIndex)
    throws IncorrectOperationException {
    PsiMethod method = methodCandidate.getElement();
    PsiSubstitutor substitutor = methodCandidate.getSubstitutor();
    // shift range should include both incompatible indexes
    for (int i = 0; i <= minIncompatibleIndex; i++) {
      for (int j = Math.max(i+2,maxIncompatibleIndex); j < expressions.length; j++) { // if j=i+1 the shift is equal to swap
        {
          ArrayUtil.rotateLeft(expressions, i, j);
          if (PsiUtil.isApplicable(method, substitutor, expressions)) {
            if (canShift(expressions, callExpression, permutations, i)) return;
          }
          ArrayUtil.rotateRight(expressions, i, j);
        }

        {
          ArrayUtil.rotateRight(expressions, i, j);
          if (PsiUtil.isApplicable(method, substitutor, expressions)) {
            if (canShift(expressions, callExpression, permutations, i)) return;
          }
          ArrayUtil.rotateLeft(expressions, i, j);
        }
      }
    }
  }

  private static boolean canShift(PsiExpression[] expressions, PsiCall callExpression, List<? super PsiCall> permutations, int i) {
    PsiCall copy = LambdaUtil.copyTopLevelCall(callExpression);
    if (copy == null) return false;
    PsiExpressionList list = copy.getArgumentList();
    if (list == null) return false;
    PsiExpression[] copyExpressions = list.getExpressions();
    for (int k = i; k < copyExpressions.length; k++) {
      copyExpressions[k].replace(expressions[k]);
    }

    JavaResolveResult result = copy.resolveMethodGenerics();
    if (result.getElement() != null && result.isValidResult()) {
      permutations.add(copy);
      if (permutations.size() > 1) return true;
    }
    return false;
  }

  private static void registerSwapFixes(final PsiExpression[] expressions, final PsiCall callExpression, final List<? super PsiCall> permutations,
                                        MethodCandidateInfo candidate, final int incompatibilitiesCount, final int minIncompatibleIndex,
                                        final int maxIncompatibleIndex) throws IncorrectOperationException {
    PsiMethod method = candidate.getElement();
    PsiSubstitutor substitutor = candidate.getSubstitutor();
    if (incompatibilitiesCount >= 3) return; // no way we can fix it by swapping

    for (int i = minIncompatibleIndex; i < maxIncompatibleIndex; i++) {
      for (int j = i+1; j <= maxIncompatibleIndex; j++) {
        ArrayUtil.swap(expressions, i, j);
        if (PsiUtil.isApplicable(method, substitutor, expressions)) {
          PsiCall copy = LambdaUtil.copyTopLevelCall(callExpression);
          if (copy == null) return;
          PsiExpressionList argumentList = copy.getArgumentList();
          if (argumentList == null) return;
          PsiExpression[] copyExpressions = argumentList.getExpressions();
          copyExpressions[i].replace(expressions[i]);
          copyExpressions[j].replace(expressions[j]);
          JavaResolveResult result = copy.resolveMethodGenerics();
          if (result.getElement() != null && result.isValidResult()) {
            permutations.add(copy);
            if (permutations.size() > 1) return;
          }
        }
        ArrayUtil.swap(expressions, i, j);
      }
    }
  }
}
