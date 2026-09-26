// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem;

import org.jetbrains.annotations.NotNull;

/**
 * This interface is mainly supported for {@link AnAction} and {@link ActionGroup}.
 * <p>
 * Specifies the thread and the way {@link AnAction#update(AnActionEvent)},
 * {@link ActionGroup#getChildren(AnActionEvent)} or other update-like methods
 * are called inside an {@link UpdateSession}.
 * <p>
 * The PREFERRED value is {@link ActionUpdateThread#BGT} as it keeps the UI thread free.
 * <p>
 * The DEFAULT value is {@link ActionUpdateThread#EDT} to make simple UI actions easier to implement.
 *
 * @see ActionUpdateThread
 */
public interface ActionUpdateThreadAware {
  /**
   * See {@link ActionUpdateThreadAware}
   */
  default @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  /**
   * Allows specifying forced action-update-thread for all actions in an action group recursively.
   */
  interface Recursive extends ActionUpdateThreadAware {
    @Override
    default @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }
  }
}
