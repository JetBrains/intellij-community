// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.dvcs;

import com.intellij.dvcs.push.PushSupport;
import com.intellij.dvcs.repo.AbstractRepositoryManager;
import com.intellij.dvcs.repo.RepoStateException;
import com.intellij.dvcs.repo.Repository;
import com.intellij.dvcs.repo.RepositoryManager;
import com.intellij.dvcs.ui.DvcsBundle;
import com.intellij.ide.file.BatchFileChangeListener;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.JdkOrderEntry;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.update.RefreshVFsSynchronously;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.CommonProcessors;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.vcs.log.TimedVcsCommit;
import com.intellij.vcs.log.VcsFullCommitDetails;
import com.intellij.vcs.log.util.VcsLogUtil;
import com.intellij.vcsUtil.VcsImplUtil;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.*;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.Callable;

public final class DvcsUtil {

  private static final Logger LOG = Logger.getInstance(DvcsUtil.class);

  private static final int IO_RETRIES = 3; // number of retries before fail if an IOException happens during file read.

  /**
   * Comparator for virtual files by name
   */
  public static final Comparator<VirtualFile> VIRTUAL_FILE_PRESENTATION_COMPARATOR = (o1, o2) -> {
    if (o1 == null && o2 == null) {
      return 0;
    }
    if (o1 == null) {
      return -1;
    }
    if (o2 == null) {
      return 1;
    }
    return o1.getPresentableUrl().compareTo(o2.getPresentableUrl());
  };

  @NotNull
  public static List<VirtualFile> sortVirtualFilesByPresentation(@NotNull Collection<? extends VirtualFile> virtualFiles) {
    return ContainerUtil.sorted(virtualFiles, VIRTUAL_FILE_PRESENTATION_COMPARATOR);
  }

  @NotNull
  public static List<VirtualFile> findVirtualFilesWithRefresh(@NotNull List<? extends File> files) {
    RefreshVFsSynchronously.refreshFiles(files);
    return ContainerUtil.mapNotNull(files, file -> VfsUtil.findFileByIoFile(file, false));
  }

  /**
   * @deprecated use {@link VcsImplUtil#getShortVcsRootName}
   */
  @NlsSafe
  @NotNull
  @Deprecated
  public static String getShortRepositoryName(@NotNull Project project, @NotNull VirtualFile root) {
    return VcsImplUtil.getShortVcsRootName(project, root);
  }

  @NlsSafe
  @NotNull
  public static String getShortRepositoryName(@NotNull Repository repository) {
    return VcsImplUtil.getShortVcsRootName(repository.getProject(), repository.getRoot());
  }

  @NlsSafe
  @NotNull
  public static String getShortNames(@NotNull Collection<? extends Repository> repositories) {
    return StringUtil.join(repositories, repository -> getShortRepositoryName(repository), ", ");
  }

  public static boolean anyRepositoryIsFresh(Collection<? extends Repository> repositories) {
    for (Repository repository : repositories) {
      if (repository.isFresh()) {
        return true;
      }
    }
    return false;
  }

  public static <T extends Repository> void disableActionIfAnyRepositoryIsFresh(@NotNull AnActionEvent e, @NotNull List<T> repositories, @NlsSafe String operationName) {
    boolean isFresh = repositories.stream().anyMatch(Repository::isFresh);
    if (isFresh) {
      Presentation p = e.getPresentation();
      p.setEnabled(false);
      p.setDescription(DvcsBundle.messagePointer("action.not.possible.in.fresh.repo.description", operationName));
    }
  }

  @Nullable
  public static String joinMessagesOrNull(@NotNull Collection<String> messages) {
    String joined = StringUtil.join(messages, "\n");
    return StringUtil.isEmptyOrSpaces(joined) ? null : joined;
  }

  /**
   * Returns the currently selected file, based on which VcsBranch or StatusBar components will identify the current repository root.
   */
  @Nullable
  public static VirtualFile getSelectedFile(@NotNull Project project) {
    FileEditor fileEditor = FileEditorManager.getInstance(project).getSelectedEditor();
    return fileEditor == null ? null : fileEditor.getFile();
  }

  @NlsSafe
  @NotNull
  public static String getShortHash(@NotNull String hash) {
    if (hash.length() < VcsLogUtil.SHORT_HASH_LENGTH) {
      LOG.debug("Unexpectedly short hash: [" + hash + "]");
    }
    if (hash.length() > VcsLogUtil.FULL_HASH_LENGTH) {
      LOG.debug("Unexpectedly long hash: [" + hash + "]");
    }
    return VcsLogUtil.getShortHash(hash);
  }

  @NlsSafe
  @NotNull
  public static String getDateString(@NotNull TimedVcsCommit commit) {
    return DateFormatUtil.formatPrettyDateTime(commit.getTimestamp()) + " ";
  }

  @NotNull
  public static AccessToken workingTreeChangeStarted(@NotNull Project project) {
    return workingTreeChangeStarted(project, null);
  }

  @NotNull
  public static AccessToken workingTreeChangeStarted(@NotNull Project project, @Nullable @Nls String activityName) {
    BackgroundTaskUtil.syncPublisher(BatchFileChangeListener.TOPIC).batchChangeStarted(project, activityName);
    return new AccessToken() {
      @Override
      public void finish() {
        BackgroundTaskUtil.syncPublisher(BatchFileChangeListener.TOPIC).batchChangeCompleted(project);
      }
    };
  }

  public static final Comparator<Repository> REPOSITORY_COMPARATOR = Comparator.comparing(Repository::getPresentableUrl);

  public static void assertFileExists(File file, @NonNls @Nls String message) throws IllegalStateException {
    if (!file.exists()) {
      throw new IllegalStateException(message);
    }
  }

  /**
   * Loads the file content.
   * Tries 3 times, then a {@link RepoStateException} is thrown.
   * Content is then trimmed and line separators get converted.
   *
   * @param file File to read.
   * @return file content.
   */
  @NlsSafe
  @NotNull
  public static String tryLoadFile(@NotNull final File file) throws RepoStateException {
    return tryLoadFile(file, null);
  }

  @NlsSafe
  @NotNull
  public static String tryLoadFile(@NotNull final File file, @Nullable String encoding) throws RepoStateException {
    return tryOrThrow(() -> StringUtil.convertLineSeparators(FileUtil.loadFile(file, encoding)).trim(), file);
  }

  @NlsSafe
  @Nullable
  @Contract("_ , !null -> !null")
  public static String tryLoadFileOrReturn(@NotNull final File file, @Nullable @NlsSafe String defaultValue) {
    return tryLoadFileOrReturn(file, defaultValue, null);
  }

  @NlsSafe
  @Nullable
  @Contract("_ , !null, _ -> !null")
  public static String tryLoadFileOrReturn(@NotNull final File file,
                                           @Nullable @NlsSafe String defaultValue,
                                           @Nullable @NonNls String encoding) {
    try {
      return tryLoadFile(file, encoding);
    }
    catch (RepoStateException e) {
      LOG.warn(e);
      return defaultValue;
    }
  }

  /**
   * Tries to execute the given action.
   * If an IOException happens, tries again up to 3 times, and then throws a {@link RepoStateException}.
   * If an other exception happens, rethrows it as a {@link RepoStateException}.
   * In the case of success returns the result of the task execution.
   */
  public static <T> T tryOrThrow(Callable<? extends T> actionToTry, Object details) throws RepoStateException {
    IOException cause = null;
    for (int i = 0; i < IO_RETRIES; i++) {
      try {
        return actionToTry.call();
      }
      catch (IOException e) {
        LOG.info("IOException while loading " + details, e);
        cause = e;
      }
      catch (Exception e) {    // this shouldn't happen since only IOExceptions are thrown in clients.
        throw new RepoStateException("Couldn't load file " + details, e);
      }
    }
    throw new RepoStateException("Couldn't load file " + details, cause);
  }

  public static void visitVcsDirVfs(@NotNull VirtualFile vcsDir, @NotNull Collection<String> subDirs) {
    vcsDir.getChildren();
    for (String subdir : subDirs) {
      VirtualFile dir = vcsDir.findFileByRelativePath(subdir);
      // process recursively, because we need to visit all branches under refs/heads and refs/remotes
      ensureAllChildrenInVfs(dir);
    }
  }

  public static void ensureAllChildrenInVfs(@Nullable VirtualFile dir) {
    if (dir != null) {
      VfsUtilCore.processFilesRecursively(dir, CommonProcessors.alwaysTrue());
    }
  }

  @RequiresEdt
  public static void addMappingIfSubRoot(@NotNull Project project,
                                         @NotNull @NonNls String newRepositoryPath,
                                         @NotNull @NonNls String vcsName) {
    if (!project.isDisposed() && project.getBasePath() != null && FileUtil.isAncestor(project.getBasePath(), newRepositoryPath, true)) {
      ProjectLevelVcsManager manager = ProjectLevelVcsManager.getInstance(project);
      manager.setDirectoryMappings(VcsUtil.addMapping(manager.getDirectoryMappings(), newRepositoryPath, vcsName));
    }
  }

  @Nullable
  @CalledInAny
  public static <T extends Repository> T guessRepositoryForFile(@NotNull Project project,
                                                                @NotNull RepositoryManager<T> manager,
                                                                @Nullable VirtualFile file,
                                                                @Nullable @NonNls String defaultRootPathValue) {
    T repository = manager.getRepositoryForRootQuick(guessVcsRoot(project, file));
    if (repository != null) return repository;
    return manager.getRepositoryForRootQuick(guessRootForVcs(project, manager.getVcs(), defaultRootPathValue));
  }

  @Nullable
  public static <T extends Repository> T guessCurrentRepositoryQuick(@NotNull Project project,
                                                                     @NotNull AbstractRepositoryManager<T> manager,
                                                                     @Nullable @NonNls String defaultRootPathValue) {
    T repository = manager.getRepositoryForRootQuick(guessVcsRoot(project, getSelectedFile(project)));
    if (repository != null) return repository;
    return manager.getRepositoryForRootQuick(guessRootForVcs(project, manager.getVcs(), defaultRootPathValue));
  }

  @Nullable
  private static VirtualFile guessRootForVcs(@NotNull Project project,
                                             @Nullable AbstractVcs vcs,
                                             @Nullable @NonNls String defaultRootPathValue) {
    if (project.isDisposed()) return null;
    LOG.debug("Guessing vcs root...");
    ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(project);
    if (vcs == null) {
      LOG.debug("Vcs not found.");
      return null;
    }
    String vcsName = vcs.getDisplayName();
    VirtualFile[] vcsRoots = vcsManager.getRootsUnderVcs(vcs);
    if (vcsRoots.length == 0) {
      LOG.debug("No " + vcsName + " roots in the project.");
      return null;
    }

    if (vcsRoots.length == 1) {
      VirtualFile onlyRoot = vcsRoots[0];
      LOG.debug("Only one " + vcsName + " root in the project, returning: " + onlyRoot);
      return onlyRoot;
    }

    // get remembered last visited repository root
    if (defaultRootPathValue != null) {
      VirtualFile recentRoot = VcsUtil.getVirtualFile(defaultRootPathValue);
      if (recentRoot != null) {
        LOG.debug("Returning the recent root: " + recentRoot);
        return recentRoot;
      }
    }

    // otherwise return the root of the project dir or the root containing the project dir, if there is such
    VirtualFile projectBaseDir = project.getBaseDir();
    if (projectBaseDir == null) {
      VirtualFile firstRoot = vcsRoots[0];
      LOG.debug("Project base dir is null, returning the first root: " + firstRoot);
      return firstRoot;
    }
    for (VirtualFile root : vcsRoots) {
      if (root.equals(projectBaseDir) || VfsUtilCore.isAncestor(root, projectBaseDir, true)) {
        LOG.debug("The best candidate: " + root);
        return root;
      }
    }
    VirtualFile rootCandidate = vcsRoots[0];
    LOG.debug("Returning the best candidate: " + rootCandidate);
    return rootCandidate;
  }

  public static <T extends Repository> List<T> sortRepositories(@NotNull Collection<? extends T> repositories) {
    List<T> validRepositories = ContainerUtil.filter(repositories, t -> t.getRoot().isValid());
    validRepositories.sort(REPOSITORY_COMPARATOR);
    return validRepositories;
  }

  @Nullable
  private static VirtualFile getVcsRootForLibraryFile(@NotNull Project project, @NotNull VirtualFile file) {
    ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(project);
    // for a file inside .jar/.zip consider the .jar/.zip file itself
    VirtualFile root = vcsManager.getVcsRootFor(VfsUtilCore.getVirtualFileForJar(file));
    if (root != null) {
      LOG.debug("Found root for zip/jar file: " + root);
      return root;
    }

    // for other libs which don't have jars inside the project dir (such as JDK) take the owner module of the lib
    List<OrderEntry> entries = ProjectRootManager.getInstance(project).getFileIndex().getOrderEntriesForFile(file);
    Set<VirtualFile> libraryRoots = new HashSet<>();
    for (OrderEntry entry : entries) {
      if (entry instanceof LibraryOrderEntry || entry instanceof JdkOrderEntry) {
        VirtualFile moduleRoot = vcsManager.getVcsRootFor(entry.getOwnerModule().getModuleFile());
        if (moduleRoot != null) {
          libraryRoots.add(moduleRoot);
        }
      }
    }

    if (libraryRoots.isEmpty()) {
      LOG.debug("No library roots");
      return null;
    }

    // if the lib is used in several modules, take the top module
    // (for modules of the same level we can't guess anything => take the first one)
    Iterator<VirtualFile> libIterator = libraryRoots.iterator();
    VirtualFile topLibraryRoot = libIterator.next();
    while (libIterator.hasNext()) {
      VirtualFile libRoot = libIterator.next();
      if (VfsUtilCore.isAncestor(libRoot, topLibraryRoot, true)) {
        topLibraryRoot = libRoot;
      }
    }
    LOG.debug("Several library roots, returning " + topLibraryRoot);
    return topLibraryRoot;
  }

  @Nullable
  public static VirtualFile guessVcsRoot(@NotNull Project project, @Nullable VirtualFile file) {
    VirtualFile root = null;
    if (file != null) {
      root = ProjectLevelVcsManager.getInstance(project).getVcsRootFor(file);
      if (root == null) {
        LOG.debug("Cannot get root by file. Trying with get by library: " + file);
        root = getVcsRootForLibraryFile(project, file);
      }
    }
    return root;
  }

  @NotNull
  @RequiresBackgroundThread
  public static <R extends Repository> Map<R, List<VcsFullCommitDetails>> groupCommitsByRoots(@NotNull RepositoryManager<R> repoManager,
                                                                                              @NotNull List<? extends VcsFullCommitDetails> commits) {
    Map<R, List<VcsFullCommitDetails>> groupedCommits = new HashMap<>();
    for (VcsFullCommitDetails commit : commits) {
      R repository = repoManager.getRepositoryForRoot(commit.getRoot());
      if (repository == null) {
        LOG.info("No repository found for commit " + commit);
        continue;
      }
      List<VcsFullCommitDetails> commitsInRoot = groupedCommits.computeIfAbsent(repository, __ -> new ArrayList<>());
      commitsInRoot.add(commit);
    }
    return groupedCommits;
  }

  @Nullable
  public static PushSupport getPushSupport(@NotNull final AbstractVcs vcs) {
    return ContainerUtil.find(PushSupport.PUSH_SUPPORT_EP.getExtensions(vcs.getProject()),
                              support -> support.getVcs().equals(vcs));
  }

  @NlsSafe
  @NotNull
  public static String joinShortNames(@NotNull Collection<? extends Repository> repositories) {
    return joinShortNames(repositories, -1);
  }

  @NlsSafe
  @NotNull
  public static String joinShortNames(@NotNull Collection<? extends Repository> repositories, int limit) {
    return joinWithAnd(ContainerUtil.map(repositories, repository -> getShortRepositoryName(repository)),
                       limit);
  }

  @Nls
  @NotNull
  public static String joinWithAnd(@NotNull List<@Nls String> strings, int limit) {
    int size = strings.size();
    if (size == 0) return "";
    if (size == 1) return strings.get(0);
    if (size == 2) return DvcsBundle.message("sequence.concatenation.a.and.b", strings.get(0), strings.get(1));

    boolean isLimited = limit >= 2 && limit < size;
    int listCount = (isLimited ? limit : size) - 1;

    @Nls StringBuilder sb = new StringBuilder();
    for (int i = 0; i < listCount; i++) {
      if (i != 0) sb.append(DvcsBundle.message("sequence.concatenation.separator"));
      sb.append(strings.get(i));
    }

    if (isLimited) {
      sb.append(DvcsBundle.message("sequence.concatenation.tail.n.others", size - limit + 1));
    }
    else {
      sb.append(DvcsBundle.message("sequence.concatenation.tail", strings.get(size - 1)));
    }
    return sb.toString();
  }
}
