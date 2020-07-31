// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project;

import com.intellij.openapi.actionSystem.ActionWithDelegate;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.util.NlsActions;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.function.Supplier;

/**
 * An action allowed to be performed in dumb mode.
 */
public abstract class DumbAwareAction extends AnAction implements DumbAware {

  @NotNull
  public static DumbAwareAction create(@NotNull Consumer<? super AnActionEvent> actionPerformed) {
    return new SimpleDumbAwareAction(actionPerformed);
  }

  @NotNull
  public static DumbAwareAction create(@Nullable @NlsActions.ActionText String text,
                                       @NotNull Consumer<? super AnActionEvent> actionPerformed) {
    return new SimpleDumbAwareAction(text, actionPerformed);
  }

  protected DumbAwareAction() {
    super((Icon)null);
  }

  protected DumbAwareAction(@Nullable Icon icon) {
    super(icon);
  }

  protected DumbAwareAction(@Nullable @NlsActions.ActionText String text) {
    super(text);
  }

  protected DumbAwareAction(@NotNull Supplier<String> dynamicText) {
    super(dynamicText);
  }

  protected DumbAwareAction(@Nullable @NlsActions.ActionText String text,
                            @Nullable @NlsActions.ActionDescription String description,
                            @Nullable Icon icon) {
    super(text, description, icon);
  }

  protected DumbAwareAction(@NotNull Supplier<String> dynamicText, @NotNull Supplier<String> dynamicDescription, @Nullable Icon icon) {
    super(dynamicText, dynamicDescription, icon);
  }

  protected DumbAwareAction(@NotNull Supplier<String> dynamicText, @NotNull Icon icon) {
    super(dynamicText, icon);
  }

  static class SimpleDumbAwareAction extends DumbAwareAction implements ActionWithDelegate<Consumer<? super AnActionEvent>> {
    private final Consumer<? super AnActionEvent> myActionPerformed;

    SimpleDumbAwareAction(Consumer<? super AnActionEvent> actionPerformed) {
      myActionPerformed = actionPerformed;
    }

    SimpleDumbAwareAction(@NlsActions.ActionText String text,
                          Consumer<? super AnActionEvent> actionPerformed) {
      super(text);
      myActionPerformed = actionPerformed;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      myActionPerformed.consume(e);
    }

    @NotNull
    @Override
    public Consumer<? super AnActionEvent> getDelegate() {
      return myActionPerformed;
    }
  }
}