/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.dvcs.repo;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsListener;
import com.intellij.openapi.vcs.VcsRoot;
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
public class VcsRepositoryManager extends AbstractProjectComponent implements Disposable, VcsListener {

  public static final Topic<VcsRepositoryMappingListener> VCS_REPOSITORY_MAPPING_UPDATED =
    Topic.create("VCS repository mapping updated", VcsRepositoryMappingListener.class);

  @NotNull private final ProjectLevelVcsManager myVcsManager;

  @NotNull private final ReentrantReadWriteLock REPO_LOCK = new ReentrantReadWriteLock();
  @NotNull private final ReentrantReadWriteLock.WriteLock MODIFY_LOCK = new ReentrantReadWriteLock().writeLock();

  @NotNull private final Map<VirtualFile, Repository> myRepositories = ContainerUtil.newHashMap();
  @NotNull private final Map<VirtualFile, Repository> myExternalRepositories = ContainerUtil.newHashMap();
  @NotNull private final List<VcsRepositoryCreator> myRepositoryCreators;

  private volatile boolean myDisposed;

  @NotNull
  public static VcsRepositoryManager getInstance(@NotNull Project project) {
    return ObjectUtils.assertNotNull(project.getComponent(VcsRepositoryManager.class));
  }

  public VcsRepositoryManager(@NotNull Project project, @NotNull ProjectLevelVcsManager vcsManager) {
    super(project);
    myVcsManager = vcsManager;
    myRepositoryCreators = Arrays.asList(Extensions.getExtensions(VcsRepositoryCreator.EXTENSION_POINT_NAME, project));
  }

  @Override
  public void initComponent() {
    myProject.getMessageBus().connect().subscribe(ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED, this);
  }

  @Override
  public void dispose() {
    myDisposed = true;
    try {
      REPO_LOCK.writeLock().lock();
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
   * @Deprecated to delete in 2017.X
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
    Repository result;
    try {
      REPO_LOCK.readLock().lock();
      if (myDisposed) {
        throw new ProcessCanceledException();
      }
      Repository repo = myRepositories.get(root);
      result = repo != null ? repo : myExternalRepositories.get(root);
    }
    finally {
      REPO_LOCK.readLock().unlock();
    }
    // if we didn't find appropriate repository, request update mappings if needed and try again
    // may be this should not be called  from several places (for example: branch widget updating from edt).
    if (updateIfNeeded && result == null && ArrayUtil.contains(root, myVcsManager.getAllVersionedRoots())) {
      checkAndUpdateRepositoriesCollection(root);
      try {
        REPO_LOCK.readLock().lock();
        return myRepositories.get(root);
      }
      finally {
        REPO_LOCK.readLock().unlock();
      }
    }
    else {
      return result;
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
    try {
      REPO_LOCK.readLock().lock();
      return !myRepositories.containsValue(repository) && myExternalRepositories.containsValue(repository);
    }
    finally {
      REPO_LOCK.readLock().unlock();
    }
  }

  @NotNull
  public Collection<Repository> getRepositories() {
    try {
      REPO_LOCK.readLock().lock();
      return Collections.unmodifiableCollection(myRepositories.values());
    }
    finally {
      REPO_LOCK.readLock().unlock();
    }
  }

  // note: we are not calling this method during the project startup - it is called anyway by f.e the GitRootTracker
  private void checkAndUpdateRepositoriesCollection(@Nullable VirtualFile checkedRoot) {
    Map<VirtualFile, Repository> repositories;
    try {
      MODIFY_LOCK.lock();
      try {
        REPO_LOCK.readLock().lock();
        if (myRepositories.containsKey(checkedRoot)) return;
        repositories = ContainerUtil.newHashMap(myRepositories);
      }
      finally {
        REPO_LOCK.readLock().unlock();
      }

      Collection<VirtualFile> invalidRoots = findInvalidRoots(repositories.keySet());
      repositories.keySet().removeAll(invalidRoots);
      Map<VirtualFile, Repository> newRoots = findNewRoots(repositories.keySet());
      repositories.putAll(newRoots);

      REPO_LOCK.writeLock().lock();
      try {
        if (!myDisposed) {
          myRepositories.clear();
          myRepositories.putAll(repositories);
        }
      }
      finally {
        REPO_LOCK.writeLock().unlock();
      }
      BackgroundTaskUtil.syncPublisher(myProject, VCS_REPOSITORY_MAPPING_UPDATED).mappingChanged();
    }
    finally {
      MODIFY_LOCK.unlock();
    }
  }

  @NotNull
  private Map<VirtualFile, Repository> findNewRoots(@NotNull Set<VirtualFile> knownRoots) {
    Map<VirtualFile, Repository> newRootsMap = ContainerUtil.newHashMap();
    for (VcsRoot root : myVcsManager.getAllVcsRoots()) {
      VirtualFile rootPath = root.getPath();
      if (rootPath != null && !knownRoots.contains(rootPath)) {
        AbstractVcs vcs = root.getVcs();
        VcsRepositoryCreator repositoryCreator = getRepositoryCreator(vcs);
        if (repositoryCreator == null) continue;
        Repository repository = repositoryCreator.createRepositoryIfValid(rootPath);
        if (repository != null) {
          newRootsMap.put(rootPath, repository);
        }
      }
    }
    return newRootsMap;
  }

  @NotNull
  private Collection<VirtualFile> findInvalidRoots(@NotNull final Collection<VirtualFile> roots) {
    final VirtualFile[] validRoots = myVcsManager.getAllVersionedRoots();
    return ContainerUtil.filter(roots, file -> !ArrayUtil.contains(file, validRoots));
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
