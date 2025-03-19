// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.libraries.ui;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;

/**
 * Base class for {@link RootDetector}'s implementations which can accept only file selected by user but not its descendants
 */
public abstract class RootFilter extends RootDetector {
  public RootFilter(OrderRootType rootType, boolean jarDirectory, final String presentableRootTypeName) {
    super(rootType, jarDirectory, presentableRootTypeName);
  }

  public abstract boolean isAccepted(@NotNull VirtualFile rootCandidate, @NotNull ProgressIndicator progressIndicator);

  @Override
  public @NotNull Collection<VirtualFile> detectRoots(@NotNull VirtualFile rootCandidate, @NotNull ProgressIndicator progressIndicator) {
    if (isAccepted(rootCandidate, progressIndicator)) {
      return Collections.singletonList(rootCandidate);
    }
    return Collections.emptyList();
  }
}
