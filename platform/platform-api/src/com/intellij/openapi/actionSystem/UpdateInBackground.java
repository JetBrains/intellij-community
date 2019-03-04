// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem;

import com.intellij.openapi.progress.ProgressManager;
import org.jetbrains.annotations.ApiStatus;

/**
 * Implement this in actions or action groups to flag that their {@link AnAction#update}, {@link ActionGroup#getChildren(AnActionEvent)}
 * and {@link ActionGroup#canBePerformed} methods can be invoked on a background thread.<p></p>
 *
 * This means that those updating methods shouldn't access Swing component hierarchy directly,
 * and any further data they access should be thread-safe.
 * The reason: it's possible that update methods are invoked concurrently from Swing thread and background thread.
 * When on background thread, application-wide read access is guaranteed, so no synchronization for PSI, VFS and project model is necessary.
 * <p></p>
 *
 * Update methods should call {@link ProgressManager#checkCanceled()} often enough to guard against UI freezes.
 */
@ApiStatus.Experimental
public interface UpdateInBackground {
}
