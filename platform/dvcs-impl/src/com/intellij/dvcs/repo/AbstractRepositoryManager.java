// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dvcs.repo;

import com.intellij.dvcs.MultiRootBranches;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.CalledInAny;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

public abstract class AbstractRepositoryManager<T extends Repository>
  implements RepositoryManager<T>, Disposable {

  private final @NotNull Project myProject;
  private final @NotNull VcsKey myVcsKey;
  private final @NotNull String myRepoDirName;
  private final @NotNull VcsRepositoryManager myGlobalRepositoryManager;

  protected AbstractRepositoryManager(@NotNull Project project,
                                      @NotNull VcsKey vcsKey,
                                      @NotNull @NonNls String repoDirName) {
    myGlobalRepositoryManager = VcsRepositoryManager.getInstance(project);
    myProject = project;
    myVcsKey = vcsKey;
    myRepoDirName = repoDirName;
  }

  @Override
  public void dispose() {
  }

  @Override
  @RequiresBackgroundThread
  public @Nullable T getRepositoryForRoot(@Nullable VirtualFile root) {
    return validateAndGetRepository(myGlobalRepositoryManager.getRepositoryForRoot(root));
  }

  @Override
  @CalledInAny
  public @Nullable T getRepositoryForRootQuick(@Nullable VirtualFile root) {
    return validateAndGetRepository(myGlobalRepositoryManager.getRepositoryForRootQuick(root));
  }

  @Override
  @CalledInAny
  public @Nullable T getRepositoryForRootQuick(@Nullable FilePath rootPath) {
    return validateAndGetRepository(myGlobalRepositoryManager.getRepositoryForRootQuick(rootPath));
  }

  @Override
  public void addExternalRepository(@NotNull VirtualFile root, @NotNull T repository) {
    myGlobalRepositoryManager.addExternalRepository(root, repository);
  }

  @Override
  public void removeExternalRepository(@NotNull VirtualFile root) {
    myGlobalRepositoryManager.removeExternalRepository(root);
  }

  @Override
  public boolean isExternal(@NotNull T repository) {
    return myGlobalRepositoryManager.isExternal(repository);
  }

  @Override
  @RequiresBackgroundThread
  public @Nullable T getRepositoryForFile(@Nullable VirtualFile file) {
    return validateAndGetRepository(myGlobalRepositoryManager.getRepositoryForFile(file));
  }

  @CalledInAny
  public @Nullable T getRepositoryForFileQuick(@Nullable VirtualFile file) {
    return validateAndGetRepository(myGlobalRepositoryManager.getRepositoryForFileQuick(file));
  }

  @Override
  @RequiresBackgroundThread
  public @Nullable T getRepositoryForFile(@Nullable FilePath file) {
    return validateAndGetRepository(myGlobalRepositoryManager.getRepositoryForFile(file, false));
  }

  @Override
  @CalledInAny
  public @Nullable T getRepositoryForFileQuick(@Nullable FilePath file) {
    return validateAndGetRepository(myGlobalRepositoryManager.getRepositoryForFile(file, true));
  }

  protected @NotNull List<T> getRepositories(Class<T> type) {
    return ContainerUtil.findAll(myGlobalRepositoryManager.getRepositories(), type);
  }

  @Override
  public abstract @NotNull List<T> getRepositories();

  @Override
  public boolean moreThanOneRoot() {
    return getRepositories().size() > 1;
  }

  @Override
  @RequiresBackgroundThread
  public void updateRepository(@Nullable VirtualFile root) {
    T repo = getRepositoryForRoot(root);
    if (repo != null) {
      repo.update();
    }
  }

  @Override
  public void updateAllRepositories() {
    ContainerUtil.process(getRepositories(), repo -> {
      repo.update();
      return true;
    });
  }

  private @Nullable T validateAndGetRepository(@Nullable Repository repository) {
    if (repository == null || !myVcsKey.equals(repository.getVcs().getKeyInstanceMethod())) {
      return null;
    }
    return ReadAction.compute(() -> {
      VirtualFile root = repository.getRoot();
      if (root.isValid()) {
        VirtualFile vcsDir = root.findChild(myRepoDirName);
        //noinspection unchecked
        return vcsDir != null && vcsDir.exists() ? (T)repository : null;
      }
      return null;
    });
  }

  @Override
  public @NotNull AbstractVcs getVcs() {
    AbstractVcs vcs = ProjectLevelVcsManager.getInstance(myProject).findVcsByName(myVcsKey.getName());
    return Objects.requireNonNull(vcs);
  }

  /**
   * Returns true if the synchronous branch control should be proposed for this project,
   * if the setting was not defined yet, and all repositories are on the same branch.
   */
  public boolean shouldProposeSyncControl() {
    return !MultiRootBranches.diverged(getRepositories());
  }
}
