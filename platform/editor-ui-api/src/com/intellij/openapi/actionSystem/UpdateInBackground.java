// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem;

import com.intellij.openapi.progress.ProgressManager;
import org.jetbrains.annotations.NotNull;

/**
 * Implement this in actions or action groups to flag that their {@link AnAction#update}, {@link ActionGroup#getChildren(AnActionEvent)}
 * methods can be invoked on a background thread.<p></p>
 *
 * This means that those updating methods shouldn't access Swing component hierarchy directly,
 * and any further data they access should be thread-safe.
 * The reason: it's possible that update methods are invoked concurrently from Swing thread and background thread.
 * When on background thread, application-wide read access is guaranteed, so no synchronization for PSI, VFS and project model is necessary.
 * <p></p>
 *
 * Update methods should call {@link ProgressManager#checkCanceled()} often enough to guard against UI freezes.
 *
 * @deprecated Use {@link AnAction#getActionUpdateThread()} or {@link ActionUpdateThreadAware} instead.
 */
@Deprecated(forRemoval = true)
public interface UpdateInBackground {
  default boolean isUpdateInBackground() {
    return true;
  }

  static boolean isUpdateInBackground(@NotNull AnAction action) {
    return action.getActionUpdateThread() == ActionUpdateThread.BGT;
  }
}
