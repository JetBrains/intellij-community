// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.ui.RootDetector;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;

public final class LibraryJavaSourceRootDetector extends RootDetector {
  public LibraryJavaSourceRootDetector() {
    super(OrderRootType.SOURCES, false, "sources");
  }

  @Override
  public @NotNull @Unmodifiable Collection<VirtualFile> detectRoots(@NotNull VirtualFile rootCandidate,
                                                                    @NotNull ProgressIndicator progressIndicator) {
    return JavaVfsSourceRootDetectionUtil.suggestRoots(rootCandidate, progressIndicator);
  }
}
