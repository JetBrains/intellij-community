// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.ide.FileIconPatcher;
import com.intellij.ide.FileIconProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public interface CoreAwareIconManager {
  /**
   * @return a deferred icon for the file, taking into account {@link com.intellij.ide.FileIconProvider} and {@link com.intellij.ide.FileIconPatcher} extensions.
   * Use {@link com.intellij.util.IconUtil#computeFileIcon} where possible (e.g. in background threads) to get a non-deferred icon.
   */
  @NotNull Icon getIcon(@NotNull VirtualFile file, @Iconable.IconFlags int flags, @Nullable Project project);

  @NotNull Runnable wakeUpNeo(@NotNull Object reason);
}
