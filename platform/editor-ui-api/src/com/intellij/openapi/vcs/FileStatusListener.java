// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jetbrains.annotations.NotNull;

/**
 * @see FileStatusManager#addFileStatusListener(FileStatusListener, Disposable)
 */
public interface FileStatusListener {
  /**
   * Indicates that some file statuses were changed.
   * The client should recalculate all statuses it's dependent on.
   * <p>
   * The callback is called with {@link ModalityState#any()}, the clients should be careful about model modifications.
   */
  @RequiresEdt
  default void fileStatusesChanged() {
  }

  /**
   * The callback is called with {@link ModalityState#any()}, the clients should be careful about model modifications.
   */
  @RequiresEdt
  default void fileStatusChanged(@NotNull VirtualFile virtualFile) {
  }
}
