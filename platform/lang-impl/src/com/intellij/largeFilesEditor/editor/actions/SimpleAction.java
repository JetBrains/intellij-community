// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.largeFilesEditor.editor.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.concurrent.Callable;

public class SimpleAction extends AnAction {
  private static final Logger logger = Logger.getInstance(SimpleAction.class);

  private final Callable<Boolean> isEnabledCallable;
  private final Runnable actionPerformedRunnable;

  public SimpleAction(@Nullable String text, @Nullable String description, @Nullable Icon icon,
                      Callable<Boolean> isEnabledCallable, Runnable actionPerformedRunnable) {
    super(text, description, icon);
    this.isEnabledCallable = isEnabledCallable;
    this.actionPerformedRunnable = actionPerformedRunnable;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    try {
      e.getPresentation().setEnabled(isEnabledCallable.call());
    }
    catch (Exception exc) {
      logger.warn(exc);
    }
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    actionPerformedRunnable.run();
  }
}