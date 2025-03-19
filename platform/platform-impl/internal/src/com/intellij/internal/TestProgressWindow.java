// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.project.DumbAwareAction;
import org.jetbrains.annotations.NotNull;

import java.util.Random;

/**
 * @author Alexander Lobas
 */
final class TestProgressWindow extends DumbAwareAction {

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    ProgressWindow p = new ProgressWindow(true, new Random().nextBoolean(), e.getProject());
    p.setTitle("Title");
    p.setIndeterminate(false);
    p.setDelayInMillis(0);

    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      try {
        p.start();
        p.setText("Starting...");
        for (double d = 0; d < 1; d += 1.0 / 80) {
          p.setFraction(d);
          Thread.sleep(100);
          p.checkCanceled();
          p.setText2("Z: " + d);
        }
      }
      catch (InterruptedException ignore) {
      }
      finally {
        p.stop();
        p.processFinish();
      }
    });
  }
}