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
package com.intellij.util.indexing;

import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import gnu.trove.THashSet;

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
      myExtensions = Extensions.getExtensions(IndexableSetContributor.EP_NAME);
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

  public AdditionalIndexableFileSet() {
  }

  @Override
  public boolean isInSet(VirtualFile file) {
    for (final VirtualFile root : getDirectories()) {
      if (VfsUtil.isAncestor(root, file, false)) {
        return true;
      }
    }
    return cachedFiles.contains(file);
  }

  @Override
  public void iterateIndexableFilesIn(VirtualFile file, ContentIterator iterator) {
    if (!isInSet(file)) return;

    if (file.isDirectory()) {
      for (VirtualFile child : file.getChildren()) {
        iterateIndexableFilesIn(child, iterator);
      }
    }
    else {
      iterator.processFile(file);
    }
  }
}
