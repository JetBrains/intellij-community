/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.mock;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class MockFileIndexFacade extends FileIndexFacade {
  private final Module myModule;
  private final List<VirtualFile> myLibraryRoots = new ArrayList<VirtualFile>();

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

  @NotNull
  @Override
  public ModificationTracker getRootModificationTracker() {
    return ModificationTracker.NEVER_CHANGED;
  }

  public void addLibraryRoot(VirtualFile file) {
    myLibraryRoots.add(file);
  }
}
