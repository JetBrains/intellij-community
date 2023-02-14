// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.intention.FileModifier;
import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class PermuteArgumentsFix implements IntentionAction, HighPriorityAction {
  private static final Logger LOG = Logger.getInstance(PermuteArgumentsFix.class);
  private final PsiCall myCall;
  private final PsiCall myPermutation;

  private PermuteArgumentsFix(@NotNull PsiCall call, @NotNull PsiCall permutation) {
    myCall = call;
    myPermutation = permutation;
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }


  @Override
  @NotNull
  public String getText() {
    return QuickFixBundle.message("permute.arguments");
  }

  @Override
  public @NotNull FileModifier getFileModifierForPreview(@NotNull PsiFile target) {
    return new PermuteArgumentsFix(PsiTreeUtil.findSameElementInCopy(myCall, target), myPermutation);
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return getText();
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return !project.isDisposed() && myCall.isValid() && BaseIntentionAction.canModify(myCall);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    Objects.requireNonNull(myCall.getArgumentList()).replace(Objects.requireNonNull(myPermutation.getArgumentList()));
  }

  public static boolean registerFix(@NotNull HighlightInfo.Builder info, PsiCall callExpression, final CandidateInfo[] candidates, final TextRange fixRange) {
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
      info.registerFix(fix, null, null, fixRange, null);
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
