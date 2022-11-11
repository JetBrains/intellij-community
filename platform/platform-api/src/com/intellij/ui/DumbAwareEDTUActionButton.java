// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.util.NlsActions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.function.Supplier;

public abstract class DumbAwareEDTUActionButton extends DumbAwareActionButton {

  public DumbAwareEDTUActionButton(@NlsActions.ActionText String text) {
    super(text);
  }

  public DumbAwareEDTUActionButton(@NlsActions.ActionText String text,
                                   @NlsActions.ActionDescription String description,
                                   @Nullable Icon icon) {
    super(text, description, icon);
  }

  public DumbAwareEDTUActionButton(@NotNull Supplier<String> dynamicText,
                                   @NotNull Supplier<String> dynamicDescription,
                                   @Nullable Icon icon) {
    super(dynamicText, dynamicDescription, icon);
  }

  public DumbAwareEDTUActionButton(@NlsActions.ActionText String text,
                                   Icon icon) {
    super(text, icon);
  }

  public DumbAwareEDTUActionButton(@NotNull Supplier<String> dynamicText, Icon icon) {
    this(dynamicText, Presentation.NULL_STRING, icon);
  }

  public DumbAwareEDTUActionButton() {
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }
}
