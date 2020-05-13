// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public interface FileStatusListener {
  /**
   * Indicates that some file statuses were change. On this event client should recalculate all statuses
   * it's dependent on.
   */
  default void fileStatusesChanged() {
  }

  default void fileStatusChanged(@NotNull VirtualFile virtualFile) {
  }
}
