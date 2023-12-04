// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.util.CachedValueImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

public final class AdditionalIndexableFileSet implements IndexableFileSet {
  private final @Nullable Project myProject;
  private final Supplier<List<IndexableSetContributor>> myExtensions;

  private final CachedValue<AdditionalIndexableRoots> myAdditionalIndexableRoots;

  public AdditionalIndexableFileSet(@Nullable Project project, @NotNull List<IndexableSetContributor> extensions) {
    myProject = project;
    myExtensions = () -> extensions;
    myAdditionalIndexableRoots = new CachedValueImpl<>(() -> new CachedValueProvider.Result<>(collectFilesAndDirectories(),
                                                                                              VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS));
  }

  public AdditionalIndexableFileSet(@Nullable Project project) {
    myProject = project;
    myExtensions = () -> IndexableSetContributor.EP_NAME.getExtensionList();
    myAdditionalIndexableRoots = new CachedValueImpl<>(() -> new CachedValueProvider.Result<>(collectFilesAndDirectories(),
                                                                                              VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS,
                                                                                              IndexableSetContributorModificationTracker.getInstance()));
  }

  private @NotNull AdditionalIndexableFileSet.AdditionalIndexableRoots collectFilesAndDirectories() {
    Set<VirtualFile> files = new HashSet<>();
    Set<VirtualFile> directories = new HashSet<>();
    for (IndexableSetContributor contributor : myExtensions.get()) {
      for (VirtualFile root : IndexableSetContributor.getRootsToIndex(contributor)) {
        if (root.isDirectory()) {
          directories.add(root);
        }
        else {
          files.add(root);
        }
      }
      if (myProject != null) {
        Set<VirtualFile> projectRoots = IndexableSetContributor.getProjectRootsToIndex(contributor, myProject);
        for (VirtualFile root : projectRoots) {
          if (root.isDirectory()) {
            directories.add(root);
          }
          else {
            files.add(root);
          }
        }
      }
    }
    return new AdditionalIndexableRoots(files, directories);
  }

  @Override
  public boolean isInSet(@NotNull VirtualFile file) {
    AdditionalIndexableRoots additionalIndexableRoots = myAdditionalIndexableRoots.getValue();
    return additionalIndexableRoots.files.contains(file) ||
           VfsUtilCore.isUnderFiles(file, additionalIndexableRoots.directories);
  }

  private record AdditionalIndexableRoots(@NotNull Set<VirtualFile> files, @NotNull Set<VirtualFile> directories) {
  }
}
