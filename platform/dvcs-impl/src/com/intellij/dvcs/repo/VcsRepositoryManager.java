// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.dvcs.repo;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * VcsRepositoryManager creates,stores and updates all Repositories information using registered {@link VcsRepositoryCreator}
 * extension point in a thread safe way.
 */
public class VcsRepositoryManager implements Disposable, VcsListener {
  public static final Topic<VcsRepositoryMappingListener> VCS_REPOSITORY_MAPPING_UPDATED =
    Topic.create("VCS repository mapping updated", VcsRepositoryMappingListener.class);

  @NotNull private final Project myProject;
  @NotNull private final ProjectLevelVcsManager myVcsManager;

  @NotNull private final ReentrantReadWriteLock REPO_LOCK = new ReentrantReadWriteLock();
  @NotNull private final ReentrantReadWriteLock.WriteLock MODIFY_LOCK = new ReentrantReadWriteLock().writeLock();

  @NotNull private final Map<VirtualFile, Repository> myRepositories = new HashMap<>();
  @NotNull private final Map<VirtualFile, Repository> myExternalRepositories = new HashMap<>();
  @NotNull private final List<VcsRepositoryCreator> myRepositoryCreators;

  private volatile boolean myDisposed;

  @NotNull
  public static VcsRepositoryManager getInstance(@NotNull Project project) {
    return ObjectUtils.assertNotNull(project.getComponent(VcsRepositoryManager.class));
  }

  public VcsRepositoryManager(@NotNull Project project, @NotNull ProjectLevelVcsManager vcsManager) {
    myProject = project;
    myVcsManager = vcsManager;
    myRepositoryCreators = Arrays.asList(VcsRepositoryCreator.EXTENSION_POINT_NAME.getExtensions(project));
    project.getMessageBus().connect().subscribe(ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED, this);
  }

  @Override
  public void dispose() {
    myDisposed = true;

    REPO_LOCK.writeLock().lock();
    try {
      myRepositories.clear();
    }
    finally {
      REPO_LOCK.writeLock().unlock();
    }
  }

  @Override
  public void directoryMappingChanged() {
    checkAndUpdateRepositoriesCollection(null);
  }

  @Nullable
  public Repository getRepositoryForFile(@NotNull VirtualFile file) {
    return getRepositoryForFile(file, false);
  }

  /**
   * @deprecated to delete in 2017.X
   */
  @Nullable
  @Deprecated
  public Repository getRepositoryForFileQuick(@NotNull VirtualFile file) {
    return getRepositoryForFile(file, true);
  }

  @Nullable
  public Repository getRepositoryForFile(@NotNull VirtualFile file, boolean quick) {
    final VcsRoot vcsRoot = myVcsManager.getVcsRootObjectFor(file);
    if (vcsRoot == null) return null;
    return quick ? getRepositoryForRootQuick(vcsRoot.getPath()) : getRepositoryForRoot(vcsRoot.getPath());
  }

  @Nullable
  public Repository getRepositoryForFile(@NotNull FilePath file, boolean quick) {
    final VcsRoot vcsRoot = myVcsManager.getVcsRootObjectFor(file);
    if (vcsRoot == null) return null;
    return quick ? getRepositoryForRootQuick(vcsRoot.getPath()) : getRepositoryForRoot(vcsRoot.getPath());
  }

  @Nullable
  public Repository getRepositoryForRootQuick(@Nullable VirtualFile root) {
    return getRepositoryForRoot(root, false);
  }

  @Nullable
  public Repository getRepositoryForRoot(@Nullable VirtualFile root) {
    return getRepositoryForRoot(root, true);
  }

  @Nullable
  private Repository getRepositoryForRoot(@Nullable VirtualFile root, boolean updateIfNeeded) {
    if (root == null) return null;

    REPO_LOCK.readLock().lock();
    try {
      if (myDisposed) {
        throw new ProcessCanceledException();
      }
      Repository repo = myRepositories.get(root);
      if (repo != null) return repo;

      Repository externalRepo = myExternalRepositories.get(root);
      if (externalRepo != null) return externalRepo;
    }
    finally {
      REPO_LOCK.readLock().unlock();
    }

    // if we didn't find appropriate repository, request update mappings if needed and try again
    // may be this should not be called  from several places (for example: branch widget updating from edt).
    if (updateIfNeeded && ArrayUtil.contains(root, myVcsManager.getAllVersionedRoots())) {
      checkAndUpdateRepositoriesCollection(root);

      REPO_LOCK.readLock().lock();
      try {
        return myRepositories.get(root);
      }
      finally {
        REPO_LOCK.readLock().unlock();
      }
    }
    else {
      return null;
    }
  }

  public void addExternalRepository(@NotNull VirtualFile root, @NotNull Repository repository) {
    REPO_LOCK.writeLock().lock();
    try {
      myExternalRepositories.put(root, repository);
    }
    finally {
      REPO_LOCK.writeLock().unlock();
    }
  }

  public void removeExternalRepository(@NotNull VirtualFile root) {
    REPO_LOCK.writeLock().lock();
    try {
      myExternalRepositories.remove(root);
    }
    finally {
      REPO_LOCK.writeLock().unlock();
    }
  }

  public boolean isExternal(@NotNull Repository repository) {
    REPO_LOCK.readLock().lock();
    try {
      return !myRepositories.containsValue(repository) && myExternalRepositories.containsValue(repository);
    }
    finally {
      REPO_LOCK.readLock().unlock();
    }
  }

  @NotNull
  public Collection<Repository> getRepositories() {
    REPO_LOCK.readLock().lock();
    try {
      return new ArrayList<>(myRepositories.values());
    }
    finally {
      REPO_LOCK.readLock().unlock();
    }
  }

  // note: we are not calling this method during the project startup - it is called anyway by f.e the GitRootTracker
  private void checkAndUpdateRepositoriesCollection(@Nullable VirtualFile checkedRoot) {
    MODIFY_LOCK.lock();
    try {
      Map<VirtualFile, Repository> repositories;

      REPO_LOCK.readLock().lock();
      try {
        repositories = new HashMap<>(myRepositories);
      }
      finally {
        REPO_LOCK.readLock().unlock();
      }

      if (checkedRoot != null && repositories.containsKey(checkedRoot)) return;

      Collection<VirtualFile> invalidRoots = findInvalidRoots(repositories.values());
      repositories.keySet().removeAll(invalidRoots);
      Map<VirtualFile, Repository> newRoots = findNewRoots(repositories.keySet());
      repositories.putAll(newRoots);

      REPO_LOCK.writeLock().lock();
      try {
        if (!myDisposed) {
          for (VirtualFile file : myRepositories.keySet()) {
            Repository oldRepo = myRepositories.get(file);
            Repository newRepo = repositories.get(file);
            if (oldRepo != newRepo) {
              Disposer.dispose(oldRepo);
            }
          }

          myRepositories.clear();
          myRepositories.putAll(repositories);
        }
      }
      finally {
        REPO_LOCK.writeLock().unlock();
      }
    }
    finally {
      MODIFY_LOCK.unlock();
    }
    BackgroundTaskUtil.syncPublisher(myProject, VCS_REPOSITORY_MAPPING_UPDATED).mappingChanged();
  }

  @NotNull
  private Map<VirtualFile, Repository> findNewRoots(@NotNull Set<VirtualFile> knownRoots) {
    Map<VirtualFile, Repository> newRootsMap = new HashMap<>();
    for (VcsRoot root : myVcsManager.getAllVcsRoots()) {
      VirtualFile rootPath = root.getPath();
      if (rootPath != null && !knownRoots.contains(rootPath)) {
        AbstractVcs vcs = root.getVcs();
        VcsRepositoryCreator repositoryCreator = getRepositoryCreator(vcs);
        if (repositoryCreator == null) continue;
        Repository repository = repositoryCreator.createRepositoryIfValid(rootPath, this);
        if (repository != null) {
          newRootsMap.put(rootPath, repository);
        }
      }
    }
    return newRootsMap;
  }

  @NotNull
  private Collection<VirtualFile> findInvalidRoots(@NotNull Collection<Repository> repositories) {
    List<VirtualFile> invalidRepos = new ArrayList<>();
    for (Repository repo : repositories) {
      VcsRoot vcsRoot = myVcsManager.getVcsRootObjectFor(repo.getRoot());
      if (vcsRoot == null ||
          !repo.getRoot().equals(vcsRoot.getPath()) ||
          !repo.getVcs().equals(vcsRoot.getVcs())) {
        invalidRepos.add(repo.getRoot());
      }
    }
    return invalidRepos;
  }

  @Nullable
  private VcsRepositoryCreator getRepositoryCreator(@Nullable final AbstractVcs vcs) {
    if (vcs == null) return null;
    return ContainerUtil.find(myRepositoryCreators, creator -> creator.getVcsKey().equals(vcs.getKeyInstanceMethod()));
  }

  @NotNull
  public String toString() {
    return "RepositoryManager{myRepositories: " + myRepositories + '}';
  }
}
