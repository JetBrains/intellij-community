// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.libraries.ui;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * Provides automatic detection of root type for files added to a library. Implementations of this class should be returned from
 * {@link LibraryRootsComponentDescriptor#getRootDetectors} method
 *
 * @see RootFilter
 * @see DescendentBasedRootFilter
 */
public abstract class RootDetector {
  private final OrderRootType myRootType;
  private final boolean myJarDirectory;
  private final String myPresentableRootTypeName;

  protected RootDetector(OrderRootType rootType, boolean jarDirectory, String presentableRootTypeName) {
    myRootType = rootType;
    myJarDirectory = jarDirectory;
    myPresentableRootTypeName = presentableRootTypeName;
  }

  public boolean isJarDirectory() {
    return myJarDirectory;
  }

  public OrderRootType getRootType() {
    return myRootType;
  }

  public String getPresentableRootTypeName() {
    return myPresentableRootTypeName;
  }

  /**
   * Find suitable roots in {@code rootCandidate} or its descendants.
   * @param rootCandidate file selected in the file chooser by user
   * @param progressIndicator can be used to show information about the progress and to abort searching if process is cancelled
   * @return suitable roots
   */
  public abstract @NotNull Collection<VirtualFile> detectRoots(@NotNull VirtualFile rootCandidate, @NotNull ProgressIndicator progressIndicator);
}
