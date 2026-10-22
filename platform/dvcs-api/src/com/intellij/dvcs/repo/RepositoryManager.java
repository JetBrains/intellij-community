// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dvcs.repo;

import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.concurrency.annotations.RequiresReadLockAbsence;
import org.jetbrains.annotations.CalledInAny;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

/**
 * The RepositoryManager stores and maintains the mapping between VCS roots (represented by {@link VirtualFile}s)
 * and {@link Repository repositories} containing information and valuable methods specific for DVCS repositories.
 */
public interface RepositoryManager<T extends Repository> {

  @NotNull
  AbstractVcs getVcs();

  /**
   * Returns the Repository instance which tracks the VCS repository located in the given root directory,
   * or {@code null} if the given root is not a valid registered vcs root.
   * <p/>
   * The method checks both project roots and external roots previously registered
   * via {@link #addExternalRepository(VirtualFile, Repository)}.
   * <p/>
   * If the repository list does not contain the root, the method updates the list and looks up the repository again.
   * Do not call this method under a read action. The update can be slow and can need a read lock in a different thread.
   * Use {@link #getRepositoryForRootQuick(VirtualFile)} when you hold a read action.
   */
  @Nullable
  @RequiresBackgroundThread
  @RequiresReadLockAbsence
  T getRepositoryForRoot(@Nullable VirtualFile root);

  boolean isExternal(@NotNull T repository);

  /**
   * Returns the {@link Repository} which the given file belongs to, or {@code null} if the file is not under any Git or Hg repository.
   * <p/>
   * If the repository list does not contain the repository, the method updates the list and looks up the repository again.
   * Do not call this method under a read action. The update can be slow and can need a read lock in a different thread.
   * Use {@link #getRepositoryForFileQuick(FilePath)} when you hold a read action.
   */
  @Nullable
  @RequiresBackgroundThread
  @RequiresReadLockAbsence
  T getRepositoryForFile(@NotNull VirtualFile file);

  /**
   * Returns the {@link Repository} which the given file belongs to, or {@code null} if the file is not under any Git or Hg repository.
   * <p/>
   * If the current repository list does not contain the repository, the method updates the list and looks up the repository again.
   * NB: Do not call this method under a read action. The update can be slow and can need a read lock in a different thread.
   *
   * @see #getRepositoryForFileQuick(FilePath)
   */
  @Nullable
  @RequiresBackgroundThread
  @RequiresReadLockAbsence
  T getRepositoryForFile(@NotNull FilePath file);

  /**
   * Returns the {@link Repository} which the given file belongs to, or {@code null} if the file is not under any Git or Hg repository.
   *
   * @see #getRepositoryForFile(FilePath)
   */
  @Nullable
  @CalledInAny
  T getRepositoryForFileQuick(@NotNull FilePath file);

  /**
   * Looks up the {@link Repository} by the given root directory, from the current repository list.
   *
   * @see #getRepositoryForRoot(VirtualFile)
   */
  @Nullable
  @CalledInAny
  T getRepositoryForRootQuick(@Nullable VirtualFile root);

  /**
   * Looks up the {@link Repository} by the given root directory, from the current repository list.
   *
   * @see #getRepositoryForRoot(VirtualFile)
   */
  @Nullable
  @CalledInAny
  T getRepositoryForRootQuick(@Nullable FilePath rootPath);

  /**
   * @return all repositories tracked by the manager.
   */
  @NotNull
  @Unmodifiable
  List<T> getRepositories();

  /**
   * Registers a repository which doesn't belong to the project.
   */
  void addExternalRepository(@NotNull VirtualFile root, @NotNull T repository);

  /**
   * Removes the repository not from the project, when it is not interesting anymore.
   */
  void removeExternalRepository(@NotNull VirtualFile root);

  boolean moreThanOneRoot();

  /**
   * Synchronously updates the specified information about repository under the given root.
   *
   * @param root root directory of the vcs repository.
   */
  void updateRepository(VirtualFile root);

  void updateAllRepositories();

  /**
   * Returns true if repositories under this repository manager are controlled synchronously.
   * <p/>
   * <b>Note:</b>
   * <p/>
   * Implementation may return a state different from the actual value stored in "DvcsSyncSettings"
   * To get only sync settings enabled,
   * use "com.intellij.dvcs.MultiRootBranches#isSyncOptionEnabled(com.intellij.dvcs.branch.DvcsSyncSettings)"
   */
  boolean isSyncEnabled();

}
