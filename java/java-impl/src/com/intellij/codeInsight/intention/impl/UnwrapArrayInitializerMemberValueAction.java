// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.PsiUpdateModCommandAction;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiArrayInitializerMemberValue;
import com.intellij.util.ArrayUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class UnwrapArrayInitializerMemberValueAction extends PsiUpdateModCommandAction<PsiArrayInitializerMemberValue> {
  private final String myInitializerText;

  private UnwrapArrayInitializerMemberValueAction(@NotNull PsiArrayInitializerMemberValue arrayValue,
                                                  @NotNull String initializerText) {
    super(arrayValue);
    myInitializerText = initializerText;
  }

  public static @Nullable UnwrapArrayInitializerMemberValueAction createFix(@NotNull PsiArrayInitializerMemberValue arrayValue) {
    final PsiAnnotationMemberValue initializer = ArrayUtil.getFirstElement(arrayValue.getInitializers());
    return (initializer != null) ? new UnwrapArrayInitializerMemberValueAction(arrayValue, initializer.getText()) : null;
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiArrayInitializerMemberValue arrayValue) {
    return arrayValue.getInitializers().length == 1 ? Presentation.of(CommonQuickFixBundle.message("fix.unwrap", myInitializerText)) : null;
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiArrayInitializerMemberValue arrayValue, @NotNull ModPsiUpdater updater) {
    new CommentTracker().replaceAndRestoreComments(arrayValue, arrayValue.getInitializers()[0]);
  }

  @Override
  public @IntentionFamilyName @NotNull String getFamilyName() {
    return QuickFixBundle.message("unwrap.array.initializer.fix");
  }
}
