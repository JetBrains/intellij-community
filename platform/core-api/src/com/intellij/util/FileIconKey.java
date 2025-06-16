// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import com.intellij.lang.Language;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.LightVirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

@ApiStatus.Internal
public final class FileIconKey {
  public final VirtualFile file;
  public final Project project;
  @Iconable.IconFlags public final int flags;
  private final @Nullable Language initialLanguage;
  private final long stamp;

  public FileIconKey(@NotNull VirtualFile file, @Nullable Project project, @Iconable.IconFlags int flags) {
    this.file = file;
    this.project = project;
    this.flags = flags;
    initialLanguage = this.file instanceof LightVirtualFile ? ((LightVirtualFile)this.file).getLanguage() : null;
    stamp = project == null ? 0 : PsiManager.getInstance(project).getModificationTracker().getModificationCount();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof FileIconKey)) return false;

    FileIconKey that = (FileIconKey)o;
    if (flags != that.flags) return false;
    if (stamp != that.stamp) return false;
    if (!file.equals(that.file)) return false;
    if (!Objects.equals(project, that.project)) return false;

    if (!Objects.equals(initialLanguage, that.initialLanguage)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return Objects.hash(file, project, flags, stamp);
  }
}