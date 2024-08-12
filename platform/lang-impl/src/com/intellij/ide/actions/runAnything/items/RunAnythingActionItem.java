// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.runAnything.items;

import com.intellij.openapi.actionSystem.AnAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public final class RunAnythingActionItem<T extends AnAction> extends RunAnythingItemBase {
  private final @NotNull T myAction;

  public RunAnythingActionItem(@NotNull T action, @NotNull String fullCommand, @Nullable Icon icon) {
    super(fullCommand, icon);
    myAction = action;
  }

  public static @NotNull String getCommand(@NotNull AnAction action, @NotNull String command) {
    return command + " " + (action.getTemplatePresentation().getText() != null ? action.getTemplatePresentation().getText() : "undefined"); //NON-NLS
  }

  @Override
  public @Nullable String getDescription() {
    return myAction.getTemplatePresentation().getDescription();
  }
}