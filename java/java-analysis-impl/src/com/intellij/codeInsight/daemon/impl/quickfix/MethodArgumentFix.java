// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiTypesUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class MethodArgumentFix extends PsiUpdateModCommandAction<PsiExpressionList> {
  private static final Logger LOG = Logger.getInstance(MethodArgumentFix.class);

  protected final int myIndex;
  protected final ArgumentFixerActionFactory myArgumentFixerActionFactory;
  protected final @NotNull PsiType myToType;

  protected MethodArgumentFix(@NotNull PsiExpressionList list, int i, @NotNull PsiType toType, @NotNull ArgumentFixerActionFactory fixerActionFactory) {
    super(list);
    myIndex = i;
    myArgumentFixerActionFactory = fixerActionFactory;
    myToType = toType instanceof PsiEllipsisType ? ((PsiEllipsisType) toType).toArrayType() : toType;
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiExpressionList list) {
    if (!myToType.isValid() || !PsiTypesUtil.allTypeParametersResolved(list, myToType)) return null;
    PsiExpression[] args = list.getExpressions();
    if (args.length <= myIndex) return null;
    PsiExpression arg = args[myIndex];
    if (arg == null || !arg.isValid()) return null;
    if (myArgumentFixerActionFactory.getModifiedArgument(arg, myToType) == null) return null;
    return Presentation.of(getText(list));
  }

  abstract @IntentionName @NotNull String getText(@NotNull PsiExpressionList list);

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiExpressionList list, @NotNull ModPsiUpdater updater) {
    PsiExpression expression = list.getExpressions()[myIndex];

    LOG.assertTrue(expression != null && expression.isValid());
    PsiExpression modified = myArgumentFixerActionFactory.getModifiedArgument(expression, myToType);
    LOG.assertTrue(modified != null, myArgumentFixerActionFactory);
    PsiElement newElement = expression.replace(modified);
    JavaCodeStyleManager.getInstance(context.project()).shortenClassReferences(newElement);
  }

  @Override
  public @NotNull String getFamilyName() {
    return QuickFixBundle.message("fix.argument.family");
  }
}
