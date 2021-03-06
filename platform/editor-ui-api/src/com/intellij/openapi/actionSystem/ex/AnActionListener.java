// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem.ex;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

/**
 * @author Kirill Kalishev
 * @author Konstantin Bulenkov
 */
public interface AnActionListener {
  @Topic.AppLevel
  Topic<AnActionListener> TOPIC = new Topic<>(AnActionListener.class, Topic.BroadcastDirection.TO_DIRECT_CHILDREN, true);

  default void beforeActionPerformed(@NotNull AnAction action, @NotNull DataContext dataContext, @NotNull AnActionEvent event) {
  }

  /**
   * Note that using {@code dataContext} in implementing methods is unsafe - it could have been invalidated by the performed action.
   */
  default void afterActionPerformed(@NotNull AnAction action, @NotNull DataContext dataContext, @NotNull AnActionEvent event) {
  }

  default void beforeEditorTyping(char c, @NotNull DataContext dataContext) {
  }

  default void afterEditorTyping(char c, @NotNull DataContext dataContext) {
  }

  /**
   * @deprecated Use {@link AnActionListener} directly.
   */
  @Deprecated
  abstract class Adapter implements AnActionListener {
  }
}
