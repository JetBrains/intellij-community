/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.notification;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiConsumer;

/**
 * @author Alexander Lobas
 */
public abstract class NotificationAction extends DumbAwareAction {
  public NotificationAction(@Nullable String text) {
    super(text);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    actionPerformed(e, Notification.get(e));
  }

  public abstract void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification);

  @NotNull
  public static NotificationAction create(@NotNull String text, @NotNull BiConsumer<AnActionEvent, Notification> performAction) {
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