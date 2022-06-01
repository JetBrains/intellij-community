// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem;

import com.intellij.util.SlowOperations;

import java.util.function.Supplier;

/**
 * Specifies the thread and the way {@link AnAction#update(AnActionEvent)} and {@link ActionGroup#getChildren(AnActionEvent)} are called.
 *
 * @see AnAction#getActionUpdateThread()
 * @see UpdateSession
 * @see SlowOperations#assertSlowOperationsAreAllowed()
 */
public enum ActionUpdateThread {
  /**
   * Background thread with all data in the provided {@link DataContext} instance.
   * <p>
   * An action methods should not access Swing component hierarchy directly.
   * All accessed models should be thread-safe (several update sessions can be run at the same time).
   * When on background thread, application-wide read access is guaranteed, so no synchronization for PSI, VFS and project model is necessary.
   * <p>
   * When the UI thread is absolutely necessary, use {@link UpdateSession#computeOnEdt(String, Supplier)}.
   */
  BGT,
  /**
   * UI thread with just UI data in the provided {@link DataContext} instance.
   * An action can access any Swing component and other UI models.
   * PSI, VFS and project models must not be used.
   */
  EDT,
  /**
   * UI thread with all data in the provided {@link DataContext} instance (can be very slow).
   *
   * @deprecated Migrate to {@link #EDT} or {@link #BGT} ASAP.
   */
  @Deprecated
  OLD_EDT
}
