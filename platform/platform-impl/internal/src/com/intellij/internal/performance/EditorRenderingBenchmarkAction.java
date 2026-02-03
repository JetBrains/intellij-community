// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.performance;

import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * Measures editor rendering performance, in FPS.
 * <p>
 * This action can also be useful for making CPU snapshots to improve the rendering.
 * <p>
 * We may enhance the action by providing a frequency distribution graph.
 */
final class EditorRenderingBenchmarkAction extends AnAction implements DumbAware {

  private static final int PERIOD = 5; // s

  private final NotificationGroup myNotificationGroup =
    NotificationGroupManager.getInstance().getNotificationGroup("editor-rendering-benchmark");

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);

    Editor editor = e.getData(CommonDataKeys.EDITOR);

    if (editor == null) {
      myNotificationGroup
        .createNotification("No active editor", MessageType.ERROR)
        .notify(project);
      return;
    }

    myNotificationGroup
      .createNotification(String.format("Benchmarking the editor for %s seconds...", PERIOD), MessageType.INFO)
      .notify(project);

    ApplicationManager.getApplication().invokeLater(() -> {
      JComponent component = editor.getComponent();
      Rectangle r = component.getVisibleRect();

      long threshold = System.currentTimeMillis() + PERIOD * 1000;

      int n = 0;
      component.setOpaque(false);
      while (true) {
        component.paintImmediately(r);
        n++;
        if (System.currentTimeMillis() >= threshold) {
          break;
        }
      }

      double fps = (double)n / PERIOD;

      myNotificationGroup
        .createNotification(String.format("Benchmark results: %.1f FPS (%d x %d)", fps, r.width, r.height), MessageType.INFO)
        .notify(project);
    });
  }
}
