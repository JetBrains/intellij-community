// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.impl.modcommand;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionActionDelegate;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.modcommand.ModCommandAction.ActionContext;
import com.intellij.modcommand.ModCommandService;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

public class ModCommandServiceImpl implements ModCommandService {
  @Override
  @NotNull
  public IntentionAction wrap(@NotNull ModCommandAction action) {
    return new ModCommandActionWrapper(action);
  }

  @Override
  public @NotNull LocalQuickFixAndIntentionActionOnPsiElement wrapToLocalQuickFixAndIntentionActionOnPsiElement(@NotNull ModCommandAction action,
                                                                                                                @NotNull PsiElement psiElement) {
    return new ModCommandActionQuickFixUberWrapper(action, psiElement);
  }

  @Override
  public @NotNull LocalQuickFix wrapToQuickFix(@NotNull ModCommandAction action) {
    return new ModCommandActionQuickFixWrapper(action);
  }

  @Override
  public @Nullable ModCommandAction unwrap(@NotNull LocalQuickFix fix) {
    if (fix instanceof ModCommandActionQuickFixWrapper wrapper) {
      return wrapper.getAction();
    }
    return null;
  }

  @Override
  @Nullable
  public ModCommandAction unwrap(@NotNull IntentionAction action) {
    while (action instanceof IntentionActionDelegate delegate) {
      action = delegate.getDelegate();
    }
    return action instanceof ModCommandActionWrapper wrapper ? wrapper.action() : 
           action instanceof ModCommandActionQuickFixUberWrapper wrapper ? wrapper.action() : null;
  }

  @Override
  public @NotNull ModCommand psiUpdate(@NotNull ActionContext context, @NotNull Consumer<@NotNull ModPsiUpdater> updater) {
    return PsiUpdateImpl.psiUpdate(context, updater);
  }
}
