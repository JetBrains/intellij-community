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
package com.intellij.util.indexing;

import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * @author peter
 */
public class AdditionalIndexableFileSet implements IndexableFileSet {
  private volatile Set<VirtualFile> cachedFiles;
  private volatile Set<VirtualFile> cachedDirectories;
  private volatile IndexedRootsProvider[] myExtensions;

  public AdditionalIndexableFileSet(IndexedRootsProvider... extensions) {
    myExtensions = extensions;
  }

  public AdditionalIndexableFileSet() {
  }

  private Set<VirtualFile> getDirectories() {
    Set<VirtualFile> directories = cachedDirectories;
    if (directories == null || filesInvalidated(directories) || filesInvalidated(cachedFiles)) {
      directories = collectFilesAndDirectories();
    }
    return directories;
  }

  private THashSet<VirtualFile> collectFilesAndDirectories() {
    THashSet<VirtualFile> files = new THashSet<VirtualFile>();
    THashSet<VirtualFile> directories = new THashSet<VirtualFile>();
    if (myExtensions == null) {
      myExtensions = Extensions.getExtensions(IndexedRootsProvider.EP_NAME);
    }
    for (IndexedRootsProvider provider : myExtensions) {
      for(VirtualFile file:IndexableSetContributor.getRootsToIndex(provider)) {
        (file.isDirectory() ? directories:files).add(file);
      }
    }
    cachedFiles = files;
    cachedDirectories = directories;
    return directories;
  }

  public static boolean filesInvalidated(Set<VirtualFile> files) {
    for (VirtualFile file : files) {
      if (!file.isValid()) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean isInSet(@NotNull VirtualFile file) {
    return VfsUtilCore.isUnder(file, getDirectories()) || cachedFiles.contains(file);
  }

  @Override
  public void iterateIndexableFilesIn(@NotNull VirtualFile file, @NotNull final ContentIterator iterator) {
    VfsUtilCore.visitChildrenRecursively(file, new VirtualFileVisitor() {
      @Override
      public boolean visitFile(@NotNull VirtualFile file) {
        if (!isInSet(file)) {
          return false;
        }

        if (!file.isDirectory()) {
          iterator.processFile(file);
        }

        return true;
      }
    });
  }
}
