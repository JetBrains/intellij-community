// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.notification;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiConsumer;
import java.util.function.Supplier;

import static com.intellij.openapi.util.NlsContexts.NotificationContent;

/**
 * @author Alexander Lobas
 * @see Notification#addAction(AnAction)
 */
public abstract class NotificationAction extends DumbAwareAction {

  public NotificationAction(@Nullable @NotificationContent String text) {
    super(text);
  }

  public NotificationAction(@NotNull Supplier<String> dynamicText) {
    super(dynamicText);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    actionPerformed(e, Notification.get(e));
  }

  public abstract void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification);

  @NotNull
  public static NotificationAction create(@NotNull @NotificationContent String text,
                                          @NotNull BiConsumer<? super AnActionEvent, ? super Notification> performAction) {
    return create(() -> text, performAction);
  }

  @NotNull
  public static NotificationAction create(@NotNull Supplier<String> dynamicText,
                                          @NotNull BiConsumer<? super AnActionEvent, ? super Notification> performAction) {
    return new NotificationAction(dynamicText) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
        performAction.accept(e, notification);
      }
    };
  }

  @NotNull
  public static NotificationAction createSimple(@NotNull Supplier<String> dynamicText, @NotNull Runnable performAction) {
    return create(dynamicText, (event, notification) -> performAction.run());
  }

  @NotNull
  public static NotificationAction createSimple(@NotNull @NotificationContent String text,
                                                @NotNull Runnable performAction) {
    return create(() -> text, (event, notification) -> performAction.run());
  }

  @NotNull
  public static NotificationAction createSimpleExpiring(@NotNull @NotificationContent String text,
                                                        @NotNull Runnable performAction) {
    return create(text, (event, notification) -> {
      performAction.run();
      notification.expire();
    });
  }
}