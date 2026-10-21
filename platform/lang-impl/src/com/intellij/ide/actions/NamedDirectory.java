// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.ide.util.gotoByName.GotoFileModel;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * A directory and the full name that the Goto File popup shows for it.
 * {@link DirectoryPathMatcher} matches a pattern against the full name, and it reads the children from the file.
 *
 * @param file     the directory
 * @param fullName the name of the directory in the popup, which is the path from the root that holds it
 */
record NamedDirectory(@NotNull VirtualFile file, @NotNull String fullName) {
  static @NotNull NamedDirectory of(@NotNull GotoFileModel model, @NotNull VirtualFile directory) {
    return new NamedDirectory(directory, StringUtil.notNullize(model.getFullName(directory)));
  }

  /** @return a directory under this one, whose full name extends the full name of this one */
  @NotNull NamedDirectory child(@NotNull VirtualFile child) {
    return new NamedDirectory(child, fullName + '/' + VfsUtilCore.getRelativePath(child, file, '/'));
  }
}
