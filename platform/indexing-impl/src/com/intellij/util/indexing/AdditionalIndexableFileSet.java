// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing;

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

  public AdditionalIndexableFileSet(@NotNull Project project, @NotNull IndexableSetContributor... extensions) {
    myProject = project;
    myExtensions = extensions;
  }

  public AdditionalIndexableFileSet(@NotNull Project project) {
    myProject = project;
  }

  AdditionalIndexableFileSet(@NotNull IndexableSetContributor... extensions) {
    myProject = null;
    myExtensions = extensions;
  }

  public AdditionalIndexableFileSet() {
    myProject = null;
  }

  @NotNull
  private Set<VirtualFile> getDirectories() {
    Set<VirtualFile> directories = cachedDirectories;
    if (directories == null || VfsUtilCore.hasInvalidFiles(directories) || VfsUtilCore.hasInvalidFiles(cachedFiles)) {
      directories = collectFilesAndDirectories();
    }
    return directories;
  }

  @NotNull
  private Set<VirtualFile> collectFilesAndDirectories() {
    if (myExtensions == null) {
      myExtensions = IndexableSetContributor.EP_NAME.getExtensions();
    }
    Set<VirtualFile> files = new THashSet<>();
    Set<VirtualFile> directories = new THashSet<>();
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
    VfsUtilCore.visitChildrenRecursively(file, new VirtualFileVisitor<Void>() {
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
