// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.newui;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.TaskInfo;
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
    super(true, task());

    myText.putClientProperty("NoFillPanelColorForDarcula", Boolean.TRUE);
    updateProgressNow();
    getComponent().setToolTipText(null);
  }

  public void setCancelRunnable(@NotNull Runnable runnable) {
    myCancelRunnable = runnable;
  }

  @Override
  protected void cancelRequest() {
    super.cancelRequest();
    myCancelRunnable.run();
  }

  @NotNull
  public JComponent createBaselineWrapper() {
    return new Wrapper(getComponent()) {
      @Override
      public int getBaseline(int width, int height) {
        return (int)(height * 0.85);
      }
    };
  }

  @NotNull
  public static TaskInfo task() {
    return new Task.Modal(null, "Downloading...", true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        // TODO: Auto-generated method stub
      }
    };
  }
}