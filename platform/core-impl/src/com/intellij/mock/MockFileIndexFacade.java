// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mock;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.UnloadedModuleDescription;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;


public class MockFileIndexFacade extends FileIndexFacade {
  private final Module myModule;
  private final Set<VirtualFile> myLibraryRoots = new HashSet<>();

  public MockFileIndexFacade(final Project project) {
    super(project);
    myModule = null;  // TODO
  }

  @Override
  public boolean isInContent(@NotNull VirtualFile file) {
    return true;
  }

  @Override
  public boolean isInSource(@NotNull VirtualFile file) {
    return true;
  }

  @Override
  public boolean isInSourceContent(@NotNull VirtualFile file) {
    return true;
  }

  @Override
  public boolean isInLibraryClasses(@NotNull VirtualFile file) {
    VirtualFile parent = file.getParent();
    while (true) {
      if (parent == null) return false;
      if (myLibraryRoots.contains(parent)) return true;
      parent = parent.getParent();
    }
  }

  @Override
  public boolean isInLibrarySource(@NotNull VirtualFile file) {
    return false;
  }

  @Override
  public boolean isExcludedFile(@NotNull VirtualFile file) {
    return false;
  }

  @Override
  public boolean isUnderIgnored(@NotNull VirtualFile file) {
    return false;
  }

  @ApiStatus.Experimental
  @Override
  public boolean isIndexable(@NotNull VirtualFile file) { return true; }

  @Override
  public Module getModuleForFile(@NotNull VirtualFile file) {
    return myModule;
  }

  @Override
  public boolean isValidAncestor(@NotNull VirtualFile baseDir, @NotNull VirtualFile child) {
    return VfsUtilCore.isAncestor(baseDir, child, false);
  }

  @Override
  public @NotNull ModificationTracker getRootModificationTracker() {
    return ModificationTracker.NEVER_CHANGED;
  }

  @Override
  public @NotNull Collection<UnloadedModuleDescription> getUnloadedModuleDescriptions() {
    return Collections.emptySet();
  }

  @Override
  public boolean isInLibrary(@NotNull VirtualFile file) {
    return isInLibraryClasses(file) || isInLibrarySource(file);
  }

  public void addLibraryRoot(VirtualFile file) {
    myLibraryRoots.add(file);
  }
}
