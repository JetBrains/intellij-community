// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.util.CachedValueImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public final class AdditionalIndexableFileSet implements IndexableFileSet {
  @Nullable
  private final Project myProject;
  private final Supplier<IndexableSetContributor[]> myExtensions;

  private final CachedValue<AdditionalIndexableRoots> myAdditionalIndexableRoots;

  public AdditionalIndexableFileSet(@Nullable Project project, IndexableSetContributor @NotNull ... extensions) {
    myProject = project;
    myExtensions = () -> extensions;
    myAdditionalIndexableRoots = new CachedValueImpl<>(() -> new CachedValueProvider.Result<>(collectFilesAndDirectories(),
                                                                                              VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS));
  }

  public AdditionalIndexableFileSet(@Nullable Project project) {
    myProject = project;
    myExtensions = () -> IndexableSetContributor.EP_NAME.getExtensions();
    myAdditionalIndexableRoots = new CachedValueImpl<>(() -> new CachedValueProvider.Result<>(collectFilesAndDirectories(),
                                                                                              VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS,
                                                                                              IndexableSetContributorModificationTracker.getInstance()));
  }

  @NotNull
  private AdditionalIndexableFileSet.AdditionalIndexableRoots collectFilesAndDirectories() {
    Set<VirtualFile> files = new HashSet<>();
    Map<IndexableSetContributor, Set<VirtualFile>> directories = new HashMap<>();
    for (IndexableSetContributor contributor : myExtensions.get()) {
      for (VirtualFile root : IndexableSetContributor.getRootsToIndex(contributor)) {
        (root.isDirectory() ? directories.computeIfAbsent(contributor, __ -> new HashSet<>()) : files).add(root);
      }
      if (myProject != null) {
        Set<VirtualFile> projectRoots = IndexableSetContributor.getProjectRootsToIndex(contributor, myProject);
        for (VirtualFile root : projectRoots) {
          (root.isDirectory() ? directories.computeIfAbsent(contributor, __ -> new HashSet<>()) : files).add(root);
        }
      }
    }
    return new AdditionalIndexableRoots(files, directories);
  }

  @Override
  public boolean isInSet(@NotNull VirtualFile file) {
    AdditionalIndexableRoots additionalIndexableRoots = myAdditionalIndexableRoots.getValue();
    if (additionalIndexableRoots.files.contains(file)) {
      return true;
    }

    for (Map.Entry<IndexableSetContributor, Set<VirtualFile>> entry : additionalIndexableRoots.directories.entrySet()) {
      IndexableSetContributor contributor = entry.getKey();
      Set<VirtualFile> directories = entry.getValue();

      VirtualFile dir = findRoot(file, directories);
      if (dir == null) continue;

      if (contributor.acceptFile(file, dir, myProject)) {
        return true;
      }
    }
    return false;
  }

  private static VirtualFile findRoot(@NotNull VirtualFile file, @Nullable Set<? extends VirtualFile> roots) {
    if (roots == null || roots.isEmpty()) return null;

    VirtualFile parent = file;
    while (parent != null) {
      if (roots.contains(parent)) {
        return parent;
      }
      parent = parent.getParent();
    }
    return null;
  }


  private static final class AdditionalIndexableRoots {
    @NotNull
    private final Set<VirtualFile> files;
    @NotNull
    private final Map<IndexableSetContributor, Set<VirtualFile>> directories;

    private AdditionalIndexableRoots(@NotNull Set<VirtualFile> files,
                                     @NotNull Map<IndexableSetContributor, Set<VirtualFile>> directories) {
      this.files = files;
      this.directories = directories;
    }
  }
}
