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
import com.intellij.openapi.project.Project;
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
  private final Project myProject;
  private volatile Set<VirtualFile> cachedFiles;
  private volatile Set<VirtualFile> cachedDirectories;
  private volatile IndexableSetContributor[] myExtensions;

  public AdditionalIndexableFileSet(Project project, IndexableSetContributor... extensions) {
    myProject = project;
    myExtensions = extensions;
  }

  public AdditionalIndexableFileSet(Project project) {
    myProject = project;
  }

  public AdditionalIndexableFileSet(IndexableSetContributor... extensions) {
    myProject = null;
    myExtensions = extensions;
  }

  public AdditionalIndexableFileSet() {
    myProject = null;
  }

  private Set<VirtualFile> getDirectories() {
    Set<VirtualFile> directories = cachedDirectories;
    if (directories == null || VfsUtilCore.hasInvalidFiles(directories) || VfsUtilCore.hasInvalidFiles(cachedFiles)) {
      directories = collectFilesAndDirectories();
    }
    return directories;
  }

  private THashSet<VirtualFile> collectFilesAndDirectories() {
    THashSet<VirtualFile> files = new THashSet<>();
    THashSet<VirtualFile> directories = new THashSet<>();
    if (myExtensions == null) {
      myExtensions = Extensions.getExtensions(IndexableSetContributor.EP_NAME);
    }
    for (IndexableSetContributor contributor : myExtensions) {
      for (VirtualFile root : IndexableSetContributor.getRootsToIndex(contributor)) {
        (root.isDirectory() ? directories : files).add(root);
      }
      if (myProject != null) {
        Set<VirtualFile> projectRoots = IndexableSetContributor.getProjectRootsToIndex(contributor, myProject);
        for (VirtualFile root : projectRoots) {
          (root.isDirectory() ? directories : files).add(root);
        }
      }
    }
    cachedFiles = files;
    cachedDirectories = directories;
    return directories;
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
