// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.UnloadedModuleDescription;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public abstract class FileIndexFacade {
  protected final Project myProject;

  protected FileIndexFacade(@NotNull Project project) {
    myProject = project;
  }

  public static FileIndexFacade getInstance(Project project) {
    return project.getService(FileIndexFacade.class);
  }

  public abstract boolean isInContent(@NotNull VirtualFile file);
  public abstract boolean isInSource(@NotNull VirtualFile file);
  public abstract boolean isInSourceContent(@NotNull VirtualFile file);
  public abstract boolean isInLibraryClasses(@NotNull VirtualFile file);

  public abstract boolean isInLibrarySource(@NotNull VirtualFile file);
  public abstract boolean isExcludedFile(@NotNull VirtualFile file);
  public abstract boolean isUnderIgnored(@NotNull VirtualFile file);

  public abstract @Nullable Module getModuleForFile(@NotNull VirtualFile file);

  /**
   * Checks if {@code file} is an ancestor of {@code baseDir} and none of the files
   * between them are excluded from the project.
   *
   * @param baseDir the parent directory to check for ancestry.
   * @param child the child directory or file to check for ancestry.
   * @return true if it's a valid ancestor, false otherwise.
   */
  public abstract boolean isValidAncestor(@NotNull VirtualFile baseDir, @NotNull VirtualFile child);

  public abstract @NotNull ModificationTracker getRootModificationTracker();

  /**
   * @return descriptions of all modules which are unloaded from the project
   * @see UnloadedModuleDescription
   */
  public abstract @NotNull Collection<UnloadedModuleDescription> getUnloadedModuleDescriptions();

  /**
   * @return true if the {@code file} is {@link #isInContent} except when it's in {@link #isInLibraryClasses} and not in {@link #isInLibrarySource}
   */
  public boolean isInProjectScope(@NotNull VirtualFile file) {
    if (isInLibraryClasses(file) && !isInSourceContent(file)) return false;

    return isInContent(file);
  }
}
