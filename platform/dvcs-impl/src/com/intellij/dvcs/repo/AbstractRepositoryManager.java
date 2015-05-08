package com.intellij.dvcs.repo;

import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class AbstractRepositoryManager<T extends Repository>
  implements RepositoryManager<T> {

  @NotNull private final AbstractVcs myVcs;
  @NotNull private final String myRepoDirName;
  @NotNull private final VcsRepositoryManager myGlobalRepositoryManager;

  protected AbstractRepositoryManager(@NotNull VcsRepositoryManager globalRepositoryManager,
                                      @NotNull AbstractVcs vcs,
                                      @NotNull String repoDirName) {
    myGlobalRepositoryManager = globalRepositoryManager;
    myVcs = vcs;
    myRepoDirName = repoDirName;
  }

  @Override
  @Nullable
  public T getRepositoryForRoot(@Nullable VirtualFile root) {
    Repository repository = myGlobalRepositoryManager.getRepositoryForRoot(root, true);
    //noinspection unchecked
    return isRootValid(repository) ? (T)repository : null;
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
  @Nullable
  public T getRepositoryForFile(@NotNull VirtualFile file) {
    return getRepositoryForFile(file, true);
  }

  @Override
  @Nullable
  public T getRepositoryForFile(@NotNull VirtualFile file, boolean updateIfNeeded) {
    Repository repository = myGlobalRepositoryManager.getRepositoryForFile(file, updateIfNeeded);
    //noinspection unchecked
    return repository != null && myVcs.equals(repository.getVcs()) ? (T)repository : null;
  }

  @Override
  @Nullable
  public T getRepositoryForFile(@NotNull FilePath file) {
    VirtualFile vFile = ChangesUtil.findValidParentAccurately(file);
    return vFile != null ? getRepositoryForFile(vFile) : null;
  }

  @NotNull
  protected List<T> getRepositories(Class<T> type) {
    return ContainerUtil.findAll(myGlobalRepositoryManager.getRepositories(), type);
  }

  @NotNull
  @Override
  public abstract List<T> getRepositories();

  @Override
  public boolean moreThanOneRoot() {
    return getRepositories().size() > 1;
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
    ContainerUtil.process(getRepositories(), new Processor<T>() {
      @Override
      public boolean process(T repo) {
        repo.update();
        return true;
      }
    });
  }

  private boolean isRootValid(@Nullable Repository repository) {
    if (repository == null || !myVcs.equals(repository.getVcs())) return false;
    VirtualFile vcsDir = repository.getRoot().findChild(myRepoDirName);
    return vcsDir != null && vcsDir.exists();
  }
}
