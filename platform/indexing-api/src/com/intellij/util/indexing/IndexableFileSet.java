/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.util.indexing;

import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.util.DeprecatedMethodException;
import org.jetbrains.annotations.NotNull;

public interface IndexableFileSet {
  boolean isInSet(@NotNull VirtualFile file);

  /**
   * @deprecated method is not used anymore.
   * We directly traverse file with {@link VfsUtilCore#visitChildrenRecursively(VirtualFile, VirtualFileVisitor)} now.
   */
  @SuppressWarnings("unused")
  @Deprecated
  default void iterateIndexableFilesIn(@NotNull VirtualFile file, @NotNull ContentIterator iterator) {
    DeprecatedMethodException.report("Unsupported method is not used anymore");
  }
}