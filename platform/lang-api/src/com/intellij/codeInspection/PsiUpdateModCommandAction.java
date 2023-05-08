// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.PsiBasedModCommandAction;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public abstract class PsiUpdateModCommandAction<E extends PsiElement> extends PsiBasedModCommandAction<E> {
  protected PsiUpdateModCommandAction(@NotNull E element) {
    super(element);
  }

  @Override
  protected final @NotNull ModCommand perform(@NotNull ActionContext context, @NotNull E element) {
    return ModCommands.psiUpdate(element, (e, upd) -> invoke(context, e, upd));
  }

  protected abstract void invoke(@NotNull ActionContext context, @NotNull E element, @NotNull EditorUpdater updater);
}
