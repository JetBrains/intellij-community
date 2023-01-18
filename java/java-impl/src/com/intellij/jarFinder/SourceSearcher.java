// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jarFinder;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Sergey Evdokimov
 */
public interface SourceSearcher {
  ExtensionPointName<SourceSearcher> EP_NAME = ExtensionPointName.create("com.intellij.sourceSearcher");

  /**
   * Returns the URL of the found source artifact, or {@code null} if none.
   */
  @Nullable
  String findSourceJar(
    @NotNull final ProgressIndicator indicator,
    @NotNull final String artifactId,
    @NotNull final String version,
    @NotNull final VirtualFile classesJar) throws SourceSearchException;
}
