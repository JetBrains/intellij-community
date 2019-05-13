// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.dvcs.ignore;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface VcsRepositoryIgnoredFilesHolder extends Disposable {

  void addUpdateStateListener(@NotNull VcsIgnoredHolderUpdateListener listener);

  void startRescan();

  boolean isInUpdateMode();

  int getSize();

  void addFiles(@NotNull Collection<? extends VirtualFile> files);

  void addFile(@NotNull VirtualFile files);

  boolean containsFile(@NotNull VirtualFile file);

  @NotNull
  List<FilePath> removeIgnoredFiles(@NotNull Collection<? extends FilePath> files);

  @NotNull
  Set<VirtualFile> getIgnoredFiles();
}
