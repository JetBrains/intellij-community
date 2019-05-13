// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.notification;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiConsumer;

/**
 * @see Notification#addAction(AnAction)
 *
 * @author Alexander Lobas
 */
public abstract class NotificationAction extends DumbAwareAction {
  public NotificationAction(@Nullable String text) {
    super(text);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    actionPerformed(e, Notification.get(e));
  }

  public abstract void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification);

  @NotNull
  public static NotificationAction create(@NotNull String text, @NotNull BiConsumer<? super AnActionEvent, ? super Notification> performAction) {
    return new NotificationAction(text) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
        performAction.accept(e, notification);
      }
    };
  }

  @NotNull
  public static NotificationAction createSimple(@NotNull String text, @NotNull Runnable performAction) {
    return create(text, (event, notification) -> performAction.run());
  }
}