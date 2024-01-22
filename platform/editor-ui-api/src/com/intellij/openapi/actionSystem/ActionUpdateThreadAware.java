// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem;

import com.intellij.diagnostic.PluginException;
import org.jetbrains.annotations.NotNull;

/**
 * This interface is supported for {@link AnAction} and can be supported in some other places.
 *
 * @see ActionUpdateThread
 */
public interface ActionUpdateThreadAware {
  /**
   * Specifies the thread and the way {@link AnAction#update(AnActionEvent)},
   * {@link ActionGroup#getChildren(AnActionEvent)} or other update-like method shall be called.
   */
  default @NotNull ActionUpdateThread getActionUpdateThread() {
    if (this instanceof UpdateInBackground && ((UpdateInBackground)this).isUpdateInBackground()) {
      return ActionUpdateThread.BGT;
    }
    PluginException.reportDeprecatedUsage(
      getClass(), "ActionUpdateThread.OLD_EDT",
      "'" + getClass().getName() + "' must override `getActionUpdateThread` and chose EDT or BGT. See ActionUpdateThread javadoc.");
    return ActionUpdateThread.OLD_EDT;
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
