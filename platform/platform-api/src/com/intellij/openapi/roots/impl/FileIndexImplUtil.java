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

package com.intellij.openapi.roots.impl;

import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import org.jetbrains.annotations.NotNull;

public class FileIndexImplUtil {
  private FileIndexImplUtil() {
  }

  public static boolean iterateRecursively(@NotNull VirtualFile root, @NotNull VirtualFileFilter filter, @NotNull ContentIterator iterator){
    if (!root.isValid() || !filter.accept(root)) return true;

    if (!iterator.processFile(root)) return false;

    if (root.isDirectory()){
      VirtualFile[] children = root.getChildren();
      for (VirtualFile child : children) {
        if (!iterateRecursively(child, filter, iterator)) return false;
      }
    }

    return true;
  }
}
