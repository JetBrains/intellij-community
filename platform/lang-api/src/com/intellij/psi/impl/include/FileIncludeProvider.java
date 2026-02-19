// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.psi.impl.include;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.util.Consumer;
import com.intellij.util.indexing.FileContent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class FileIncludeProvider {

  public abstract @NotNull String getId();

  public abstract boolean acceptFile(@NotNull VirtualFile file);

  public boolean acceptFile(@NotNull VirtualFile file, Project project) {
    return acceptFile(file);
  }

  public abstract void registerFileTypesUsedForIndexing(@NotNull Consumer<? super FileType> fileTypeSink);

  public abstract FileIncludeInfo @NotNull [] getIncludeInfos(@NotNull FileContent content);

  /**
   * If all providers return {@code null} then {@code FileIncludeInfo} is resolved in a standard way using {@code FileReferenceSet}
   */
  public @Nullable PsiFileSystemItem resolveIncludedFile(final @NotNull FileIncludeInfo info, final @NotNull PsiFile context) {
    return null;
  }

  /**
   * Override this method and increment returned value each time when you change the logic of your provider.
   */
  public int getVersion() {
    return 0;
  }

  /**
   * @return  Possible name in included paths. For example if a provider returns FileIncludeInfos without file extensions 
   */
  public @NotNull String getIncludeName(@NotNull PsiFile file, @NotNull String originalName) {
    return originalName;
  }
}
