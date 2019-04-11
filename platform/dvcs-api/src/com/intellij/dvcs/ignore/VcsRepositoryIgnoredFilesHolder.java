// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.dvcs.ignore;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface VcsRepositoryIgnoredFilesHolder extends Disposable {

  void addUpdateStateListener(@NotNull VcsIgnoredHolderUpdateListener listener);

  void startRescan();

  void startRescan(@Nullable Runnable actionAfterRescan);

  boolean isInUpdateMode();

  int getSize();

  @Deprecated
  void addFiles(@NotNull Collection<? extends VirtualFile> files);

  void addFilePaths(@NotNull Collection<? extends FilePath> files);

  @Deprecated
  void addFile(@NotNull VirtualFile file);

  void addFile(@NotNull FilePath file);

  @Deprecated
  boolean containsFile(@NotNull VirtualFile file);

  boolean containsFile(@NotNull FilePath file);

  @NotNull
  List<FilePath> removeIgnoredFiles(@NotNull Collection<? extends FilePath> files);

  @Deprecated
  @NotNull
  Set<VirtualFile> getIgnoredFiles();

  @NotNull
  Set<FilePath> getIgnoredFilePaths();
}
