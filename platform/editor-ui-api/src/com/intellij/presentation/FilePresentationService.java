// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.presentation;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public interface FilePresentationService {

  static @NotNull FilePresentationService getInstance(@NotNull Project project) {
    return project.getService(FilePresentationService.class);
  }

  static @Nullable Color getFileBackgroundColor(@Nullable Project project, @Nullable VirtualFile file) {
    if (project == null || project.isDisposed()) return null;
    if (file == null || !file.isValid()) return null;
    return getInstance(project).getFileBackgroundColor(file);
  }

  /**
   * @return background color of a file, taking into account extensions like {@link com.intellij.ui.FileColorManager#getFileColor(VirtualFile)}
   */
  @RequiresReadLock
  @RequiresBackgroundThread
  @Nullable Color getFileBackgroundColor(@NotNull VirtualFile file);

  /**
   * @return background color of the {@link PsiElement#getContainingFile containing file} of an {@code element},
   * or background color of directory, which corresponds to an element, etc.
   */
  @RequiresReadLock
  @RequiresBackgroundThread
  @Nullable Color getFileBackgroundColor(@NotNull PsiElement element);
}
