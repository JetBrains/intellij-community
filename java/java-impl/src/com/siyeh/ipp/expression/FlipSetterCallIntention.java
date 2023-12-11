// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ipp.expression;

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.psi.*;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ipp.base.MCIntention;
import com.siyeh.ipp.base.PsiElementContextPredicate;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.PsiSelectionSearcher;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public final class FlipSetterCallIntention extends MCIntention {
  @Override
  protected @NotNull String getTextForElement(@NotNull PsiElement element) {
    return getFamilyName();
  }

  @Override
  public @NotNull String getFamilyName() {
    return IntentionPowerPackBundle.message("flip.setter.call.intention.family.name");
  }

  @Override
  protected void processIntention(@NotNull PsiElement element, @NotNull ActionContext context, @NotNull ModPsiUpdater updater) {
    if (!context.selection().isEmpty()) {
      final List<PsiMethodCallExpression> methodCalls =
        PsiSelectionSearcher.searchElementsInSelection(element.getContainingFile(), context.selection(), PsiMethodCallExpression.class, false);
      if (!methodCalls.isEmpty()) {
        for (PsiMethodCallExpression call : methodCalls) {
          PsiMethodCallExpression flipped = flipCall(call);
          if (flipped != null) {
            updater.highlight(flipped);
            updater.moveTo(flipped);
          }
        }
        return;
      }
    }
    if (element instanceof PsiMethodCallExpression call) {
      flipCall(call);
    }
  }

  @Override
  @NotNull
  protected PsiElementPredicate getElementPredicate() {
    return new SetterCallPredicate();
  }

  private static PsiMethodCallExpression flipCall(PsiMethodCallExpression call) {
    final PsiExpression[] arguments = call.getArgumentList().getExpressions();
    if (arguments.length != 1) return null;
    final PsiExpression argument = PsiUtil.skipParenthesizedExprDown(arguments[0]);
    if (!(argument instanceof PsiMethodCallExpression call2)) return null;

    final PsiExpression qualifierExpression1 = ExpressionUtils.getEffectiveQualifier(call.getMethodExpression());
    final PsiExpression qualifierExpression2 = ExpressionUtils.getEffectiveQualifier(call2.getMethodExpression());
    if (qualifierExpression1 == null || qualifierExpression2 == null) return null;
    final PsiMethod setter = call.resolveMethod();
    final PsiMethod getter = call2.resolveMethod();
    final PsiMethod get = PropertyUtil.getReversePropertyMethod(setter);
    final PsiMethod set = PropertyUtil.getReversePropertyMethod(getter);
    if (get == null || set == null) return null;
    CommentTracker ct = new CommentTracker();
    final String text =
      ct.text(qualifierExpression2) + "." + set.getName() + "(" + ct.text(qualifierExpression1) + "." + get.getName() + "())";
    return (PsiMethodCallExpression)ct.replaceAndRestoreComments(call, text);
  }

  private static boolean isSetGetMethodCall(PsiElement element) {
    if (!(element instanceof PsiMethodCallExpression call1)) {
      return false;
    }
    final PsiExpression[] arguments = call1.getArgumentList().getExpressions();
    if (arguments.length != 1) {
      return false;
    }
    final PsiExpression argument = PsiUtil.skipParenthesizedExprDown(arguments[0]);
    if (!(argument instanceof PsiMethodCallExpression call2)) {
      return false;
    }
    final PsiMethod setter = call1.resolveMethod();
    final PsiMethod getter = call2.resolveMethod();
    final PsiMethod get = PropertyUtil.getReversePropertyMethod(setter);
    final PsiMethod set = PropertyUtil.getReversePropertyMethod(getter);
    if (setter == null || getter == null || get == null || set == null) {
      return false;
    }

    //check types compatibility
    final PsiParameter[] parameters = setter.getParameterList().getParameters();
    if (parameters.length != 1) {
      return false;
    }
    final PsiParameter parameter = parameters[0];
    return parameter.getType().equals(getter.getReturnType());
  }

  private static class SetterCallPredicate extends PsiElementContextPredicate {
    @Override
    public boolean satisfiedBy(PsiElement element, @NotNull ActionContext context) {
      if (!context.selection().isEmpty()) {
        final List<PsiMethodCallExpression> list =
          PsiSelectionSearcher.searchElementsInSelection(context.file(), context.selection(), PsiMethodCallExpression.class, false);
        for (PsiMethodCallExpression methodCallExpression : list) {
          if (isSetGetMethodCall(methodCallExpression)) {
            return true;
          }
        }
      }
      return isSetGetMethodCall(element);
    }
  }
}
