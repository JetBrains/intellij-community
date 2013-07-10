package com.intellij.dvcs.repo;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * @author Nadya Zabrodina
 */
public abstract class AbstractRepositoryManager<T extends Repository> extends AbstractProjectComponent
  implements Disposable, RepositoryManager<T>, VcsListener {

  private static final Logger LOG = Logger.getInstance(RepositoryManager.class);

  @NotNull private final ProjectLevelVcsManager myVcsManager;
  @NotNull private final AbstractVcs myVcs;
  @NotNull private final String myRepoDirName;

  @NotNull protected final Map<VirtualFile, T> myRepositories = new HashMap<VirtualFile, T>();

  @NotNull protected final ReentrantReadWriteLock REPO_LOCK = new ReentrantReadWriteLock();
  @NotNull private final CountDownLatch myInitializationWaiter = new CountDownLatch(1);

  protected AbstractRepositoryManager(@NotNull Project project, @NotNull ProjectLevelVcsManager vcsManager, @NotNull AbstractVcs vcs,
                                      @NotNull String repoDirName) {
    super(project);
    myVcsManager = vcsManager;
    myVcs = vcs;
    myRepoDirName = repoDirName;
  }

  @Override
  public void initComponent() {
    Disposer.register(myProject, this);
    myProject.getMessageBus().connect().subscribe(ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED, this);
  }

  @Override
  public void dispose() {
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
    updateRepositoriesCollection();
  }

  @Override
  @Nullable
  public T getRepositoryForRoot(@Nullable VirtualFile root) {
    if (root == null) {
      return null;
    }
    try {
      REPO_LOCK.readLock().lock();
      return myRepositories.get(root);
    }
    finally {
      REPO_LOCK.readLock().unlock();
    }
  }

  @Override
  @Nullable
  public T getRepositoryForFile(@NotNull VirtualFile file) {
    final VcsRoot vcsRoot = myVcsManager.getVcsRootObjectFor(file);
    return getRepositoryForVcsRoot(vcsRoot, file.getPath());
  }

  @Override
  public T getRepositoryForFile(@NotNull FilePath file) {
    final VcsRoot vcsRoot = myVcsManager.getVcsRootObjectFor(file);
    return getRepositoryForVcsRoot(vcsRoot, file.getPath());
  }

  @Nullable
  private T getRepositoryForVcsRoot(@Nullable VcsRoot vcsRoot, @NotNull String filePath) {
    if (vcsRoot == null) {
      return null;
    }
    final AbstractVcs vcs = vcsRoot.getVcs();
    if (!myVcs.equals(vcs)) {
      if (vcs != null) {
        LOG.debug(String.format("getRepositoryForFile returned non-(%s) root for file %s", myVcs.getDisplayName(), filePath));
      }
      return null;
    }
    return getRepositoryForRoot(vcsRoot.getPath());
  }

  @Override
  @NotNull
  public List<T> getRepositories() {
    try {
      REPO_LOCK.readLock().lock();
      return RepositoryUtil.sortRepositories(myRepositories.values());
    }
    finally {
      REPO_LOCK.readLock().unlock();
    }
  }

  @Override
  public boolean moreThanOneRoot() {
    return myRepositories.size() > 1;
  }

  @Override
  public void updateRepository(@Nullable VirtualFile root) {
    T repo = getRepositoryForRoot(root);
    if (repo != null) {
      repo.update();
    }
  }

  @Override
  public void updateAllRepositories() {
    Map<VirtualFile, T> repositories;
    try {
      REPO_LOCK.readLock().lock();
      repositories = new HashMap<VirtualFile, T>(myRepositories);
    }
    finally {
      REPO_LOCK.readLock().unlock();
    }

    for (VirtualFile root : repositories.keySet()) {
      updateRepository(root);
    }
  }

  // note: we are not calling this method during the project startup - it is called anyway by f.e the GitRootTracker
  private void updateRepositoriesCollection() {
    Map<VirtualFile, T> repositories;
    try {
      REPO_LOCK.readLock().lock();
      repositories = new HashMap<VirtualFile, T>(myRepositories);
    }
    finally {
      REPO_LOCK.readLock().unlock();
    }

    final VirtualFile[] roots = myVcsManager.getRootsUnderVcs(myVcs);
    // remove repositories that are not in the roots anymore
    for (Iterator<Map.Entry<VirtualFile, T>> iterator = repositories.entrySet().iterator(); iterator.hasNext(); ) {
      if (!ArrayUtil.contains(iterator.next().getValue().getRoot(), roots)) {
        iterator.remove();
      }
    }
    // add Repositories for all roots that don't have correspondent appropriate Git or Hg Repositories yet.
    for (VirtualFile root : roots) {
      if (!repositories.containsKey(root)) {
        if (isRootValid(root)) {
          try {
            T repository = createRepository(root);
            repositories.put(root, repository);
          }
          catch (RepoStateException e) {
            LOG.error("Couldn't initialize Repository in " + root.getPresentableUrl(), e);
          }
        }
        else {
          LOG.info("Invalid vcs root: " + root);
        }
      }
    }

    REPO_LOCK.writeLock().lock();
    try {
      myRepositories.clear();
      myRepositories.putAll(repositories);
      myInitializationWaiter.countDown();
    }
    finally {
      REPO_LOCK.writeLock().unlock();
    }
  }

  private boolean isRootValid(@NotNull VirtualFile root) {
    VirtualFile vcsDir = root.findChild(myRepoDirName);
    return vcsDir != null && vcsDir.exists();
  }

  @NotNull
  protected abstract T createRepository(@NotNull VirtualFile root);

  @Override
  @NotNull
  public String toString() {
    return "RepositoryManager{myRepositories: " + myRepositories + '}';
  }

  @Override
  public void waitUntilInitialized() {
    try {
      myInitializationWaiter.await();
    }
    catch (InterruptedException e) {
      LOG.error(e);
    }
  }

}
