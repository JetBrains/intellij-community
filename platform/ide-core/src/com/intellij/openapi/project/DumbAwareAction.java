// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project;

import com.intellij.ide.lightEdit.LightEditCompatible;
import com.intellij.openapi.actionSystem.ActionWithDelegate;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.util.NlsActions;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * An action allowed to be performed in dumb mode.
 *
 * @see DumbAware
 */
public abstract class DumbAwareAction extends AnAction implements DumbAware {
  public static @NotNull DumbAwareAction create(@NotNull Consumer<? super AnActionEvent> actionPerformed) {
    return new SimpleDumbAwareAction(actionPerformed);
  }

  public static @NotNull DumbAwareAction create(@Nullable @NlsActions.ActionText String text,
                                                @NotNull Consumer<? super AnActionEvent> actionPerformed) {
    return new SimpleDumbAwareAction(text, actionPerformed);
  }

  public static @NotNull DumbAwareAction create(@Nullable Icon icon,
                                                @NotNull Consumer<? super AnActionEvent> actionPerformed) {
    return new SimpleDumbAwareAction(icon, actionPerformed);
  }

  public static @NotNull DumbAwareAction create(@Nullable @NlsActions.ActionText String text,
                                                @Nullable Icon icon,
                                                @NotNull Consumer<? super AnActionEvent> actionPerformed) {
    DumbAwareAction action = new SimpleDumbAwareAction(text, actionPerformed);
    action.getTemplatePresentation().setIcon(icon);
    return action;
  }

  protected DumbAwareAction() {
    super();
  }

  protected DumbAwareAction(@Nullable Icon icon) {
    super(icon);
  }

  protected DumbAwareAction(@Nullable @NlsActions.ActionText String text) {
    super(text);
  }

  protected DumbAwareAction(@NotNull Supplier<@NlsActions.ActionText String> dynamicText) {
    super(dynamicText);
  }

  protected DumbAwareAction(@Nullable @NlsActions.ActionText String text,
                            @Nullable @NlsActions.ActionDescription String description,
                            @Nullable Icon icon) {
    super(text, description, icon);
  }

  protected DumbAwareAction(@NotNull Supplier<@NlsActions.ActionText String> text,
                            @Nullable Supplier<@NlsActions.ActionDescription String> description,
                            @Nullable Supplier<? extends @Nullable Icon> iconSupplier) {
    super(text, Objects.requireNonNullElse(description, Presentation.NULL_STRING), iconSupplier);
  }

  protected DumbAwareAction(@NotNull Supplier<@NlsActions.ActionText String> dynamicText,
                            @NotNull Supplier<@NlsActions.ActionDescription String> dynamicDescription,
                            @Nullable Icon icon) {
    super(dynamicText, dynamicDescription, icon);
  }

  protected DumbAwareAction(@NotNull Supplier<@NlsActions.ActionText String> dynamicText,
                            @NotNull Supplier<@NlsActions.ActionDescription String> dynamicDescription) {
    super(dynamicText, dynamicDescription);
  }

  protected DumbAwareAction(@NotNull Supplier<@NlsActions.ActionText String> dynamicText, @Nullable Icon icon) {
    super(dynamicText, icon);
  }

  @ApiStatus.Internal
  public static class SimpleDumbAwareAction extends DumbAwareAction implements ActionWithDelegate<java.util.function.Consumer<? super AnActionEvent>>,
                                                                        LightEditCompatible {
    private final java.util.function.Consumer<? super AnActionEvent> myActionPerformed;

    SimpleDumbAwareAction(java.util.function.Consumer<? super AnActionEvent> actionPerformed) {
      myActionPerformed = actionPerformed;
    }

    SimpleDumbAwareAction(@NlsActions.ActionText String text,
                          java.util.function.Consumer<? super AnActionEvent> actionPerformed) {
      super(text);
      myActionPerformed = actionPerformed;
    }

    SimpleDumbAwareAction(@Nullable Icon icon,
                          java.util.function.Consumer<? super AnActionEvent> actionPerformed) {
      super(icon);
      myActionPerformed = actionPerformed;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      myActionPerformed.accept(e);
    }

    @Override
    public @NotNull java.util.function.Consumer<? super AnActionEvent> getDelegate() {
      return myActionPerformed;
    }
  }
}
