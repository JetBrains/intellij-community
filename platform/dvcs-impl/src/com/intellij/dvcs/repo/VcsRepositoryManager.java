// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dvcs.repo;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsRoot;
import com.intellij.openapi.vcs.impl.VcsInitObject;
import com.intellij.openapi.vcs.impl.VcsStartupActivity;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Alarm;
import com.intellij.util.ArrayUtil;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.CalledInAny;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * VcsRepositoryManager creates,stores and updates all repository's information using registered {@link VcsRepositoryCreator}
 * extension point in a thread safe way.
 */
@Service(Service.Level.PROJECT)
public final class VcsRepositoryManager implements Disposable {
  public static final ExtensionPointName<VcsRepositoryCreator> EP_NAME = new ExtensionPointName<>("com.intellij.vcsRepositoryCreator");

  private static final Logger LOG = Logger.getInstance(VcsRepositoryManager.class);

  /**
   * VCS repository mapping updated. Project level.
   */
  public static final Topic<VcsRepositoryMappingListener> VCS_REPOSITORY_MAPPING_UPDATED = new Topic<>(VcsRepositoryMappingListener.class, Topic.BroadcastDirection.NONE);

  private final @NotNull Project myProject;
  private final @NotNull ProjectLevelVcsManager myVcsManager;

  private final @NotNull ReentrantReadWriteLock REPO_LOCK = new ReentrantReadWriteLock();
  private final @NotNull ReentrantReadWriteLock.WriteLock MODIFY_LOCK = new ReentrantReadWriteLock().writeLock();

  private final @NotNull Map<VirtualFile, Repository> myRepositories = new HashMap<>();
  private final @NotNull Map<VirtualFile, Repository> myExternalRepositories = new HashMap<>();

  private final Alarm myUpdateAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, this);
  private volatile boolean myDisposed;

  public static @NotNull VcsRepositoryManager getInstance(@NotNull Project project) {
    return Objects.requireNonNull(project.getService(VcsRepositoryManager.class));
  }

  public VcsRepositoryManager(@NotNull Project project) {
    myProject = project;
    myVcsManager = ProjectLevelVcsManager.getInstance(project);
    project.getMessageBus().connect(this).subscribe(ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED, this::scheduleUpdate);

    EP_NAME.addChangeListener(() -> {
      disposeAllRepositories(false);
      scheduleUpdate();
      BackgroundTaskUtil.syncPublisher(myProject, VCS_REPOSITORY_MAPPING_UPDATED).mappingChanged();
    }, this);
  }

  static final class MyStartupActivity implements VcsStartupActivity {
    @Override
    public void runActivity(@NotNull Project project) {
      getInstance(project).checkAndUpdateRepositoriesCollection(null);
    }

    @Override
    public int getOrder() {
      return VcsInitObject.OTHER_INITIALIZATION.getOrder();
    }
  }

  @Override
  public void dispose() {
    myDisposed = true;
    disposeAllRepositories(true);
  }

  private void disposeAllRepositories(boolean disposeExternal) {
    REPO_LOCK.writeLock().lock();
    try {
      for (Repository repo : myRepositories.values()) {
        Disposer.dispose(repo);
      }
      myRepositories.clear();

      if (disposeExternal) {
        for (Repository repo : myExternalRepositories.values()) {
          Disposer.dispose(repo);
        }
        myExternalRepositories.clear();
      }
    }
    finally {
      REPO_LOCK.writeLock().unlock();
    }
  }

  private void scheduleUpdate() {
    if (myUpdateAlarm.isDisposed()) return;
    myUpdateAlarm.cancelAllRequests();
    myUpdateAlarm.addRequest(() -> checkAndUpdateRepositoriesCollection(null), 0);
  }

  @RequiresBackgroundThread
  public @Nullable Repository getRepositoryForFile(@NotNull VirtualFile file) {
    return getRepositoryForFile(file, false);
  }

  @CalledInAny
  public @Nullable Repository getRepositoryForFileQuick(@NotNull VirtualFile file) {
    return getRepositoryForFile(file, true);
  }

  public @Nullable Repository getRepositoryForFile(@NotNull VirtualFile file, boolean quick) {
    final VcsRoot vcsRoot = myVcsManager.getVcsRootObjectFor(file);
    if (vcsRoot == null) {
      return getExternalRepositoryForFile(file);
    }
    return quick ? getRepositoryForRootQuick(vcsRoot.getPath()) : getRepositoryForRoot(vcsRoot.getPath());
  }

  public @Nullable Repository getRepositoryForFile(@NotNull FilePath file, boolean quick) {
    final VcsRoot vcsRoot = myVcsManager.getVcsRootObjectFor(file);
    if (vcsRoot == null) {
      return getExternalRepositoryForFile(file);
    }
    return quick ? getRepositoryForRootQuick(vcsRoot.getPath()) : getRepositoryForRoot(vcsRoot.getPath());
  }

  public @Nullable Repository getExternalRepositoryForFile(@NotNull VirtualFile file) {
    Map<VirtualFile, Repository> repositories = getExternalRepositories();
    for (Map.Entry<VirtualFile, Repository> entry : repositories.entrySet()) {
      if (entry.getKey().isValid() && VfsUtilCore.isAncestor(entry.getKey(), file, false)) {
        return entry.getValue();
      }
    }
    return null;
  }

  public @Nullable Repository getExternalRepositoryForFile(@NotNull FilePath file) {
    Map<VirtualFile, Repository> repositories = getExternalRepositories();
    for (Map.Entry<VirtualFile, Repository> entry : repositories.entrySet()) {
      if (entry.getKey().isValid() && FileUtil.isAncestor(entry.getKey().getPath(), file.getPath(), false)) {
        return entry.getValue();
      }
    }
    return null;
  }

  public @Nullable Repository getRepositoryForRootQuick(@Nullable VirtualFile root) {
    return getRepositoryForRoot(root, false);
  }

  public @Nullable Repository getRepositoryForRoot(@Nullable VirtualFile root) {
    return getRepositoryForRoot(root, true);
  }

  private @Nullable Repository getRepositoryForRoot(@Nullable VirtualFile root, boolean updateIfNeeded) {
    if (root == null) return null;

    Application application = ApplicationManager.getApplication();
    if (updateIfNeeded && application.isDispatchThread() &&
        !application.isUnitTestMode() && !application.isHeadlessEnvironment()) {
      updateIfNeeded = false;
      LOG.error("Do not call synchronous repository update in EDT");
    }

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

  public @NotNull Collection<Repository> getRepositories() {
    REPO_LOCK.readLock().lock();
    try {
      return new ArrayList<>(myRepositories.values());
    }
    finally {
      REPO_LOCK.readLock().unlock();
    }
  }

  private @NotNull Map<VirtualFile, Repository> getExternalRepositories() {
    REPO_LOCK.readLock().lock();
    try {
      return new HashMap<>(myExternalRepositories);
    }
    finally {
      REPO_LOCK.readLock().unlock();
    }
  }

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

  private @NotNull Map<VirtualFile, Repository> findNewRoots(@NotNull Set<VirtualFile> knownRoots) {
    Map<VirtualFile, Repository> newRootsMap = new HashMap<>();
    for (VcsRoot root : myVcsManager.getAllVcsRoots()) {
      VirtualFile rootPath = root.getPath();
      if (!knownRoots.contains(rootPath)) {
        Repository repository = tryCreateRepository(myProject, root.getVcs(), rootPath, this);
        if (repository != null) {
          newRootsMap.put(rootPath, repository);
        }
      }
    }
    return newRootsMap;
  }

  private @NotNull Collection<VirtualFile> findInvalidRoots(@NotNull Collection<Repository> repositories) {
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

  private static @Nullable Repository tryCreateRepository(@NotNull Project project,
                                                          @Nullable AbstractVcs vcs,
                                                          @NotNull VirtualFile rootPath,
                                                          @NotNull Disposable disposable) {
    if (vcs == null) return null;
    return EP_NAME.computeSafeIfAny(creator -> {
      if (creator.getVcsKey().equals(vcs.getKeyInstanceMethod())) {
        return creator.createRepositoryIfValid(project, rootPath, disposable);
      }
      return null;
    });
  }

  public @NotNull String toString() {
    return "RepositoryManager(repositories=" + myRepositories + ')'; // NON-NLS
  }

  @TestOnly
  public void checkAndUpdateRepositories() {
    checkAndUpdateRepositoriesCollection(null);
  }

  @TestOnly
  public void waitForAsyncTaskCompletion() {
    try {
      myUpdateAlarm.waitForAllExecuted(10, TimeUnit.SECONDS);
    }
    catch (Exception e) {
      LOG.error(e);
    }
  }
}
