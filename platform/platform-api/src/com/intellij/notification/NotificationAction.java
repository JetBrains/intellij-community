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
    return new Simple(dynamicText, performAction, performAction);
  }

  @NotNull
  public static NotificationAction createSimple(@NotNull Supplier<String> dynamicText, @NotNull Runnable performAction) {
    return new Simple(dynamicText, (event, notification) -> performAction.run(), performAction);
  }

  @NotNull
  public static NotificationAction createSimple(@NotNull @NotificationContent String text,
                                                @NotNull Runnable performAction) {
    return new Simple(() -> text, (event, notification) -> performAction.run(), performAction);
  }

  @NotNull
  public static NotificationAction createSimpleExpiring(@NotNull @NotificationContent String text,
                                                        @NotNull Runnable performAction) {
    return new Simple(() -> text, (event, notification) -> {
      performAction.run();
      notification.expire();
    }, performAction);
  }

  public static class Simple extends NotificationAction {
    private @NotNull final BiConsumer<? super AnActionEvent, ? super Notification> myPerformAction;
    private @NotNull final Object myActionInstance; // for FUS

    public Simple(@NotNull Supplier<String> dynamicText,
                  @NotNull BiConsumer<? super AnActionEvent, ? super Notification> performAction,
                  @NotNull Object actionInstance) {
      super(dynamicText);
      myPerformAction = performAction;
      myActionInstance = actionInstance;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
      myPerformAction.accept(e, notification);
    }

    public Object getActionInstance() {
      return myActionInstance;
    }
  }
}