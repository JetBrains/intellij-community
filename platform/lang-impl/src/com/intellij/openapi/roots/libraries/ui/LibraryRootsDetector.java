// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.libraries.ui;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.roots.libraries.LibraryRootType;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public abstract class LibraryRootsDetector {
  /**
   * Find suitable roots in {@code rootCandidate} or its descendants.
   *
   * @param rootCandidate file selected in the file chooser by user
   * @param progressIndicator can be used to show information about the progress and to abort searching if process is cancelled
   * @return suitable roots
   */
  public abstract Collection<DetectedLibraryRoot> detectRoots(@NotNull VirtualFile rootCandidate,
                                                              @NotNull ProgressIndicator progressIndicator);

  /**
   * @return presentable name for the root type or {@code null} if the root type isn't supported by this detector
   */
  public abstract @Nullable String getRootTypeName(@NotNull LibraryRootType rootType);
}
