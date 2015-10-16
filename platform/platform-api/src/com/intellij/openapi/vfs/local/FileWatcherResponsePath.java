/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.vfs.local;

import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * On OS X (and potentially other situations) when a file is dirty, the parent directory may be sent to the File Watcher as dirty. This
 * causes problems with watch roots.
 *
 * For flat roots, it means that if and only if the exact dirty file path is returned, we should compare the parent to the flat roots,
 * otherwise we should compare to path given to us because it is already the parent of the actual dirty path
 *
 * For recursive roots, if the path given to us is already the parent of the actual dirty path, we need to compare the path to the parent
 * of the recursive root because if the root itself was changed, we need to know about it.
 */
public class FileWatcherResponsePath {
  @NotNull private final String myPath;
  private final boolean myIsParentOfDirtyPath;

  public FileWatcherResponsePath(@NotNull String path, boolean isParentOfDirtyPath) {
    this.myPath = path;
    this.myIsParentOfDirtyPath = isParentOfDirtyPath;
  }

  @NotNull
  public String getPath() {
    return myPath;
  }

  public boolean isParentOfDirtyPath() {
    return myIsParentOfDirtyPath;
  }

  @NotNull
  public static Collection<FileWatcherResponsePath> toFileWatcherResponsePaths(Collection<String> paths, boolean isParentOfDirtyPath) {
    ImmutableList.Builder<FileWatcherResponsePath> builder = ImmutableList.builder();
    for (String path : paths) {
      builder.add(new FileWatcherResponsePath(path, isParentOfDirtyPath));
    }
    return builder.build();
  }
}
