// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.CommonIntentionAction;
import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.psi.*;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.psiutils.CommentTracker;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.function.Consumer;

public final class MoveParenthesisFix extends PsiUpdateModCommandAction<PsiCallExpression> {
  private final int myPos;
  private final int myShiftSize;

  public MoveParenthesisFix(PsiCallExpression call, int pos, int shiftSize) {
    super(call);
    myPos = pos;
    myShiftSize = shiftSize;
  }

  @Override
  public @NotNull @IntentionFamilyName String getFamilyName() {
    return QuickFixBundle.message("intention.move.parenthesis.name");
  }

  @Override
  protected @NotNull Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiCallExpression element) {
    return Presentation.of(getFamilyName()).withPriority(PriorityAction.Priority.HIGH);
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiCallExpression call, @NotNull ModPsiUpdater updater) {
    PsiCallExpression copy = copyWithShift(call, myPos, myShiftSize);
    if (copy != null) {
      new CommentTracker().replaceAndRestoreComments(call, copy);
    }
  }
  
  private static PsiCallExpression copyWithShift(PsiCallExpression parentCall, int pos, int shift) {
    PsiCallExpression parentCopy = (PsiCallExpression)parentCall.copy();
    PsiExpressionList parentArgsCopy = Objects.requireNonNull(parentCopy.getArgumentList());
    PsiCallExpression childCopy = (PsiCallExpression)parentArgsCopy.getExpressions()[pos];
    PsiExpressionList childArgsCopy = Objects.requireNonNull(childCopy.getArgumentList());
    if (shift > 0) {
      if (shift >= childArgsCopy.getExpressionCount()) {
        return null;
      }
      for(int i=0; i<shift; i++) {
        PsiExpression lastArg = ArrayUtil.getLastElement(childArgsCopy.getExpressions());
        assert lastArg != null;
        parentArgsCopy.addAfter(lastArg, childCopy);
        lastArg.delete();
      }
    }
    if (shift < 0) {
      if (parentArgsCopy.getExpressionCount() <= pos - shift) {
        return null;
      }
      for (int i = 0; i > shift; i--) {
        PsiExpression nextArg = parentArgsCopy.getExpressions()[pos + 1];
        childArgsCopy.add(nextArg);
        nextArg.delete();
      }
    }
    return parentCopy;
  }

  public static boolean registerFix(@NotNull Consumer<? super CommonIntentionAction> info, PsiCallExpression callExpression, final CandidateInfo[] candidates) {
    PsiExpressionList parent = ObjectUtils.tryCast(callExpression.getParent(), PsiExpressionList.class);
    if (parent == null) return false;
    PsiCallExpression parentCall = ObjectUtils.tryCast(parent.getParent(), PsiCallExpression.class);
    if (parentCall == null) return false;
    PsiExpressionList argList = callExpression.getArgumentList();
    if (argList == null) return false;
    PsiExpression[] args = argList.getExpressions();
    if (args.length == 0) return false;
    PsiExpression[] parentArgs = parent.getExpressions();
    int pos = ArrayUtil.indexOf(parentArgs, callExpression);
    if (pos == -1) return false;
    IntSet shifts = new IntOpenHashSet();
    for (CandidateInfo candidate : candidates) {
      PsiMethod candidateMethod = ObjectUtils.tryCast(candidate.getElement(), PsiMethod.class);
      if (candidateMethod == null || candidateMethod.isVarArgs()) return false;
      int count = candidateMethod.getParameterList().getParametersCount();
      if (count == 0 || count == args.length) return false;
      shifts.add(args.length - count);
    }
    if (shifts.isEmpty()) return false;
    MoveParenthesisFix fix = null;
    for (int shift : shifts) {
      PsiCallExpression copy = copyWithShift(parentCall, pos, shift);
      if (copy == null) continue;
      JavaResolveResult parentResolve = copy.resolveMethodGenerics();
      if (!parentResolve.isValidResult()) continue;
      var childCopy = (PsiCallExpression)Objects.requireNonNull(copy.getArgumentList()).getExpressions()[pos];
      JavaResolveResult childResolve = childCopy.resolveMethodGenerics();
      if (!childResolve.isValidResult()) continue;
      if (fix != null) return false;
      fix = new MoveParenthesisFix(parentCall, pos, shift);
    }
    if (fix == null) return false;
    info.accept(fix);
    return true;
  }
}
