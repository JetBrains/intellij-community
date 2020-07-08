// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots;

import com.intellij.openapi.vfs.LocalFileSystem;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public interface WatchedRootsProvider {
  /**
   * @return paths which should be monitored via {@link LocalFileSystem#addRootToWatch(java.lang.String, boolean)}.
   * @see LocalFileSystem
   */
  @NotNull Set<String> getRootsToWatch();
}