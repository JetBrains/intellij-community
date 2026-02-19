// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem;

import com.intellij.util.SlowOperations;

/**
 * Specifies the thread and the way {@link AnAction#update(AnActionEvent)}, {@link ActionGroup#getChildren(AnActionEvent)}
 * or other update-like methods shall be called.
 * <p>
 * The update session is run on a background thread.
 * Thus, {@link #BGT} is the preferred value for all actions:
 * <ol>
 *   <li>actions with trivial or fast update logic will not require thread switching</li>
 *   <li>actions with complex update logic will not freeze the UI thread while keeping the possibility to switch to it</li>
 * </ol>
 *
 * @see AnAction#getActionUpdateThread()
 * @see UpdateSession
 * @see SlowOperations#assertSlowOperationsAreAllowed()
 */
public enum ActionUpdateThread {
  /**
   * Background thread with all data in the provided {@link DataContext} instance (<b>PREFERRED</b>).
   * <p>
   * An action method must not access Swing component hierarchy directly.
   * All accessed models must be thread-safe (several update sessions can be run at the same time).
   * When on background thread, application-wide read access is guaranteed, so no synchronization for PSI, VFS and project model is necessary.
   * <p>
   * When the UI thread is absolutely necessary, use {@link UpdateSession#compute}.
   */
  BGT,
  /**
   * UI thread with just UI data in the provided {@link DataContext} instance.
   * An action can access any Swing component and other UI models.
   * PSI, VFS and project models must not be used.
   */
  EDT,
}
