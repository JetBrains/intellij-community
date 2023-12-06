// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.TaskInfo;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.wm.impl.status.InlineProgressIndicator;
import com.intellij.ui.components.panels.Wrapper;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Alexander Lobas
 */
public class OneLineProgressIndicator extends InlineProgressIndicator {
  private Runnable myCancelRunnable;

  public OneLineProgressIndicator() {
    this(true);
  }

  public OneLineProgressIndicator(boolean withText) {
    this(withText, true);
  }

  public OneLineProgressIndicator(boolean withText, boolean canBeCancelled) {
    super(true, task(withText ? IdeBundle.message("progress.text.downloading") : "", canBeCancelled));

    if (!withText) {
      text.getParent().remove(text);
    }
    updateProgressNow();
    getComponent().setToolTipText(null);
  }

  public void setCancelRunnable(@NotNull Runnable runnable) {
    myCancelRunnable = runnable;
  }

  @Override
  protected void cancelRequest() {
    super.cancelRequest();
    if (myCancelRunnable != null) {
      myCancelRunnable.run();
    }
  }

  public @NotNull JComponent createBaselineWrapper() {
    return new Wrapper(getComponent()) {
      @Override
      public int getBaseline(int width, int height) {
        return (int)(height * 0.85);
      }
    };
  }

  public static @NotNull TaskInfo task(@NotNull @NlsContexts.DialogTitle String title) {
    return task(title, true);
  }

  private static @NotNull TaskInfo task(@NotNull @NlsContexts.DialogTitle String title, boolean canBeCancelled) {
    return new Task.Modal(null, title, canBeCancelled) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
      }
    };
  }
}