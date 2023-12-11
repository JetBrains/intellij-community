// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ipp.collections;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiLambdaExpression;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.collections.ImmutableCollectionModelUtils.ImmutableCollectionModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ReplaceWithMutableCollectionIntention extends PsiUpdateModCommandAction<PsiMethodCallExpression> {
  
  public ReplaceWithMutableCollectionIntention() {
    super(PsiMethodCallExpression.class);
  }

  @Override
  public @NotNull String getFamilyName() {
    return IntentionPowerPackBundle.message("replace.with.mutable.collection.intention.family.name");
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiMethodCallExpression call, @NotNull ModPsiUpdater updater) {
    ImmutableCollectionModel model = ImmutableCollectionModelUtils.createModel(call);
    if (model == null) return;
    ImmutableCollectionModelUtils.replaceWithMutable(model, updater);
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiMethodCallExpression call) {
    if (!JavaLanguage.INSTANCE.equals(call.getLanguage())) return null;
    if (call !=
        PsiTreeUtil.getParentOfType(context.findLeaf(), PsiMethodCallExpression.class, true, PsiClass.class, PsiLambdaExpression.class)) {
      return null;
    }
    ImmutableCollectionModel model = ImmutableCollectionModelUtils.createModel(call);
    if (model == null) return null;
    return Presentation.of(IntentionPowerPackBundle.message("replace.with.mutable.collection.intention.intention.name", model.getText()));
  }
}
