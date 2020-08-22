// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.util.CachedValueImpl;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.function.Supplier;

public class AdditionalIndexableFileSet implements IndexableFileSet {
  @Nullable
  private final Project myProject;
  private final boolean myOnlyProjectPart;
  private final Supplier<IndexableSetContributor[]> myExtensions;

  private final CachedValue<AdditionalIndexableRoots> myAdditionalIndexableRoots;

  public AdditionalIndexableFileSet(@Nullable Project project, IndexableSetContributor @NotNull ... extensions) {
    myProject = project;
    myOnlyProjectPart = false;
    myExtensions = () -> extensions;
    myAdditionalIndexableRoots = new CachedValueImpl<>(() -> new CachedValueProvider.Result<>(collectFilesAndDirectories(),
                                                                                              VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS));
  }

  public AdditionalIndexableFileSet(@Nullable Project project, boolean onlyProjectPart) {
    myProject = project;
    myExtensions = () -> IndexableSetContributor.EP_NAME.getExtensions();
    myAdditionalIndexableRoots = new CachedValueImpl<>(() -> new CachedValueProvider.Result<>(collectFilesAndDirectories(),
                                                                                              VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS,
                                                                                              IndexableSetContributorModificationTracker.getInstance()));
    myOnlyProjectPart = onlyProjectPart;
  }

  public AdditionalIndexableFileSet(@Nullable Project project) {
    this(project, false);
  }

  public AdditionalIndexableFileSet() {
    this(null);
  }

  @NotNull
  private AdditionalIndexableFileSet.AdditionalIndexableRoots collectFilesAndDirectories() {
    Set<VirtualFile> files = new THashSet<>();
    Set<VirtualFile> directories = new THashSet<>();
    for (IndexableSetContributor contributor : myExtensions.get()) {
      if (myProject == null || !myOnlyProjectPart) {
        for (VirtualFile root : IndexableSetContributor.getRootsToIndex(contributor)) {
          (root.isDirectory() ? directories : files).add(root);
        }
      }
      if (myProject != null) {
        Set<VirtualFile> projectRoots = IndexableSetContributor.getProjectRootsToIndex(contributor, myProject);
        for (VirtualFile root : projectRoots) {
          (root.isDirectory() ? directories : files).add(root);
        }
      }
    }
    return new AdditionalIndexableRoots(files, directories);
  }

  @Override
  public boolean isInSet(@NotNull VirtualFile file) {
    AdditionalIndexableRoots additionalIndexableRoots = myAdditionalIndexableRoots.getValue();
    return VfsUtilCore.isUnder(file, additionalIndexableRoots.directories) || additionalIndexableRoots.files.contains(file);
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

  private static final class AdditionalIndexableRoots {
    @NotNull
    private final Set<VirtualFile> files;
    @NotNull
    private final Set<VirtualFile> directories;

    private AdditionalIndexableRoots(@NotNull Set<VirtualFile> files, @NotNull Set<VirtualFile> directories) {
      this.files = files;
      this.directories = directories;
    }
  }
}
