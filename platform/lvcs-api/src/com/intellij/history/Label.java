// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.history;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public interface Label {
  Label NULL_INSTANCE = new Label() {

    @Override
    public void revert(@NotNull Project project, @NotNull VirtualFile file) {
    }

    @Override
    public ByteContent getByteContent(String path) {
      return null;
    }
  };

  /**
   * Revert all changes up to this Label according to the local history
   *
   * @param file file or directory that should be reverted
   * @throws LocalHistoryException
   */
  void revert(@NotNull Project project, @NotNull VirtualFile file) throws LocalHistoryException;

  ByteContent getByteContent(String path);
}
