// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project;

import com.intellij.openapi.actionSystem.ActionWithDelegate;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * An action allowed to be performed in dumb mode.
 *
 * @author nik
 */
public abstract class DumbAwareAction extends AnAction implements DumbAware {

  @NotNull
  public static DumbAwareAction create(@NotNull Consumer<? super AnActionEvent> actionPerformed) {
    return new SimpleDumbAwareAction(actionPerformed);
  }

  @NotNull
  public static DumbAwareAction create(@Nullable @Nls(capitalization = Nls.Capitalization.Title) String text,
                                       @NotNull Consumer<? super AnActionEvent> actionPerformed) {
    return new SimpleDumbAwareAction(text, actionPerformed);
  }

  protected DumbAwareAction() {
  }

  protected DumbAwareAction(@Nullable @Nls(capitalization = Nls.Capitalization.Title) String text) {
    super(text);
  }

  protected DumbAwareAction(@Nullable @Nls(capitalization = Nls.Capitalization.Title) String text,
                            @Nullable @Nls(capitalization = Nls.Capitalization.Sentence) String description,
                            @Nullable Icon icon) {
    super(text, description, icon);
  }

  private static class SimpleDumbAwareAction extends DumbAwareAction implements ActionWithDelegate<Consumer<? super AnActionEvent>> {
    private final Consumer<? super AnActionEvent> myActionPerformed;

    private SimpleDumbAwareAction(Consumer<? super AnActionEvent> actionPerformed) {
      myActionPerformed = actionPerformed;
    }

    private SimpleDumbAwareAction(@Nls(capitalization = Nls.Capitalization.Title) String text, Consumer<? super AnActionEvent> actionPerformed) {
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