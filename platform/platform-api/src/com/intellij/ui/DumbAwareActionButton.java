// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.util.NlsActions.ActionText;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.function.Supplier;

/**
 * AnActionButton reinvents the action update wheel and breaks MVC.
 * We are slowly migrating to regular {@link AnAction}.
 *
 * @deprecated Use regular {@link com.intellij.openapi.project.DumbAwareAction}
 */
@Deprecated(forRemoval = true)
public abstract class DumbAwareActionButton extends AnActionButton implements DumbAware {

  public DumbAwareActionButton(@ActionText String text) {
    super(text);
  }

  public DumbAwareActionButton(@ActionText String text,
                               @NlsActions.ActionDescription String description,
                               @Nullable Icon icon) {
    super(text, description, icon);
  }

  public DumbAwareActionButton(@NotNull Supplier<String> dynamicText,
                               @NotNull Supplier<String> dynamicDescription,
                               @Nullable Icon icon) {
    super(dynamicText, dynamicDescription, icon);
  }

  public DumbAwareActionButton(@ActionText String text,
                               Icon icon) {
    super(text, icon);
  }

  public DumbAwareActionButton(@NotNull Supplier<String> dynamicText, Icon icon) {
    this(dynamicText, Presentation.NULL_STRING, icon);
  }

  public DumbAwareActionButton() {
  }
}
