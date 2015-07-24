/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsListener;
import com.intellij.openapi.vcs.VcsRoot;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * VcsRepositoryManager creates, stores and updates all Repositories information using registered {@link VcsRepositoryCreator}
 * extension point in a thread safe way.
 */
public class VcsRepositoryManager {
  @NotNull private final ProjectLevelVcsManager myVcsManager;

  @NotNull private final ReentrantReadWriteLock repoLock = new ReentrantReadWriteLock();
  @NotNull private final ReentrantReadWriteLock.WriteLock modifyLock = new ReentrantReadWriteLock().writeLock();

  @NotNull private final Map<VirtualFile, Repository> myRepositories = ContainerUtil.newHashMap();
  @NotNull private final Map<VirtualFile, Repository> myExternalRepositories = ContainerUtil.newHashMap();
  @NotNull private final List<VcsRepositoryCreator> myRepositoryCreators;

  public VcsRepositoryManager(@NotNull Project project, @NotNull ProjectLevelVcsManager vcsManager) {
    myVcsManager = vcsManager;
    myRepositoryCreators = Arrays.asList(Extensions.getExtensions(VcsRepositoryCreator.EXTENSION_POINT_NAME, project));
  }

  public static VcsRepositoryManager getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, VcsRepositoryManager.class);
  }

  public static final class MyStartUpActivity implements StartupActivity, DumbAware {
    @Override
    public void runActivity(@NotNull final Project project) {
      if (!project.isDefault() && !ApplicationManager.getApplication().isUnitTestMode()) {
        project.getMessageBus().connect().subscribe(ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED, new VcsListener() {
          @Override
          public void directoryMappingChanged() {
            getInstance(project).checkAndUpdateRepositoriesCollection(null);
          }
        });
      }
    }
  }

  @TestOnly
  public void addListener(@NotNull Project project) {
    project.getMessageBus().connect().subscribe(ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED, new VcsListener() {
      @Override
      public void directoryMappingChanged() {
        checkAndUpdateRepositoriesCollection(null);
      }
    });
  }

  @Nullable
  public Repository getRepositoryForFile(@NotNull VirtualFile file) {
    final VcsRoot vcsRoot = myVcsManager.getVcsRootObjectFor(file);
    return vcsRoot != null ? getRepositoryForRoot(vcsRoot.getPath()) : null;
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
      repoLock.readLock().lock();
      Repository repo = myRepositories.get(root);
      result = repo != null ? repo : myExternalRepositories.get(root);
    }
    finally {
      repoLock.readLock().unlock();
    }
    // if we didn't find appropriate repository, request update mappings if needed and try again
    // may be this should not be called  from several places (for example: branch widget updating from edt).
    if (updateIfNeeded && result == null && ArrayUtil.contains(root, myVcsManager.getAllVersionedRoots())) {
      checkAndUpdateRepositoriesCollection(root);
      try {
        repoLock.readLock().lock();
        return myRepositories.get(root);
      }
      finally {
        repoLock.readLock().unlock();
      }
    }
    else {
      return result;
    }
  }

  public void addExternalRepository(@NotNull VirtualFile root, @NotNull Repository repository) {
    repoLock.writeLock().lock();
    try {
      myExternalRepositories.put(root, repository);
    }
    finally {
      repoLock.writeLock().unlock();
    }
  }

  public void removeExternalRepository(@NotNull VirtualFile root) {
    repoLock.writeLock().lock();
    try {
      myExternalRepositories.remove(root);
    }
    finally {
      repoLock.writeLock().unlock();
    }
  }

  public boolean isExternal(@NotNull Repository repository) {
    try {
      repoLock.readLock().lock();
      return !myRepositories.containsValue(repository) && myExternalRepositories.containsValue(repository);
    }
    finally {
      repoLock.readLock().unlock();
    }
  }

  @NotNull
  public Collection<Repository> getRepositories() {
    try {
      repoLock.readLock().lock();
      return Collections.unmodifiableCollection(myRepositories.values());
    }
    finally {
      repoLock.readLock().unlock();
    }
  }

  // note: we are not calling this method during the project startup - it is called anyway by f.e the GitRootTracker
  private void checkAndUpdateRepositoriesCollection(@Nullable VirtualFile checkedRoot) {
    Map<VirtualFile, Repository> repositories;
    try {
      modifyLock.lock();
      try {
        repoLock.readLock().lock();
        if (myRepositories.containsKey(checkedRoot)) {
          return;
        }
        repositories = ContainerUtil.newHashMap(myRepositories);
      }
      finally {
        repoLock.readLock().unlock();
      }

      Collection<VirtualFile> invalidRoots = findInvalidRoots(repositories.keySet());
      repositories.keySet().removeAll(invalidRoots);
      Map<VirtualFile, Repository> newRoots = findNewRoots(repositories.keySet());
      repositories.putAll(newRoots);

      repoLock.writeLock().lock();
      try {
        myRepositories.clear();
        myRepositories.putAll(repositories);
      }
      finally {
        repoLock.writeLock().unlock();
      }
    }
    finally {
      modifyLock.unlock();
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
    return ContainerUtil.filter(roots, new Condition<VirtualFile>() {
      @Override
      public boolean value(VirtualFile file) {
        return !ArrayUtil.contains(file, validRoots);
      }
    });
  }

  @Nullable
  private VcsRepositoryCreator getRepositoryCreator(@Nullable final AbstractVcs vcs) {
    if (vcs == null) return null;
    return ContainerUtil.find(myRepositoryCreators, new Condition<VcsRepositoryCreator>() {
      @Override
      public boolean value(VcsRepositoryCreator creator) {
        return creator.getVcsKey().equals(vcs.getKeyInstanceMethod());
      }
    });
  }

  @NotNull
  public String toString() {
    return "RepositoryManager{myRepositories: " + myRepositories + '}';
  }
}
