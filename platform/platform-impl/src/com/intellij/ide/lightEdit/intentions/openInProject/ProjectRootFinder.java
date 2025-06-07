// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.lightEdit.intentions.openInProject;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

@ApiStatus.Internal
public abstract class ProjectRootFinder {
  @Nullable VirtualFile findProjectRoot(@NotNull VirtualFile sourceFile) {
    VirtualFile parent = sourceFile.getParent();
    while (parent != null) {
      if (isProjectDir(parent)) {
        return parent;
      }
      ProgressManager.checkCanceled();
      parent = parent.getParent();
    }
    return null;
  }

  protected abstract boolean isProjectDir(@NotNull VirtualFile file);

  protected abstract boolean requiresConfirmation();

  protected boolean containsChild(@NotNull VirtualFile file, @NotNull Predicate<? super VirtualFile> predicate) {
    if (file.isDirectory()) {
      for (VirtualFile child : file.getChildren()) {
        if (predicate.test(child)) return true;
      }
    }
    return false;
  }
}
