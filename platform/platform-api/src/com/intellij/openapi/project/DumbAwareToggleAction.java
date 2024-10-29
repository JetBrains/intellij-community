// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project;

import com.intellij.openapi.actionSystem.ToggleAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.function.Supplier;

import static com.intellij.openapi.util.NlsActions.ActionDescription;
import static com.intellij.openapi.util.NlsActions.ActionText;

public abstract class DumbAwareToggleAction extends ToggleAction implements DumbAware {
  protected DumbAwareToggleAction() { }

  protected DumbAwareToggleAction(@Nullable @ActionText String text) {
    this(() -> text);
  }

  protected DumbAwareToggleAction(@NotNull Supplier<String> text) {
    super(text);
  }

  protected DumbAwareToggleAction(@Nullable @ActionText String text, @Nullable @ActionDescription String description, @Nullable Icon icon) {
    this(() -> text, () -> description, icon);
  }

  protected DumbAwareToggleAction(@NotNull Supplier<String> dynamicText, @NotNull Supplier<String> dynamicDescription, @Nullable Icon icon) {
    super(dynamicText, dynamicDescription, icon);
  }
}
