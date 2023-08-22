// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mock;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.UnloadedModuleDescription;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;


public class MockFileIndexFacade extends FileIndexFacade {
  private final Module myModule;
  private final List<VirtualFile> myLibraryRoots = new ArrayList<>();

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
    for (VirtualFile libraryRoot : myLibraryRoots) {
      if (VfsUtilCore.isAncestor(libraryRoot, file, false)) {
        return true;
      }
    }
    return false;
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
