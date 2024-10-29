// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dvcs;

import com.intellij.dvcs.push.PushSupport;
import com.intellij.dvcs.repo.*;
import com.intellij.dvcs.ui.DvcsBundle;
import com.intellij.history.ActivityId;
import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryAction;
import com.intellij.ide.file.BatchFileChangeListener;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.JdkOrderEntry;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.NaturalComparator;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.impl.projectlevelman.NewMappings;
import com.intellij.openapi.vcs.update.RefreshVFsSynchronously;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.CommonProcessors;
import com.intellij.util.ObjectUtils;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.vcs.log.TimedVcsCommit;
import com.intellij.vcs.log.VcsCommitMetadata;
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

  public static <T extends Repository> void disableActionIfAnyRepositoryIsFresh(@NotNull AnActionEvent e,
                                                                                @NotNull Collection<T> repositories,
                                                                                @Nls String operationName) {
    boolean isFresh = ContainerUtil.exists(repositories, Repository::isFresh);
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
  @RequiresEdt
  public static VirtualFile getSelectedFile(@NotNull Project project) {
    FileEditor fileEditor = FileEditorManager.getInstance(project).getSelectedEditor();
    return fileEditor == null ? null : fileEditor.getFile();
  }

  /**
   * @deprecated Prefer {@link #guessWidgetRepository} or {@link #guessRepositoryForOperation}.
   */
  @Nullable
  @Deprecated(forRemoval = true)
  @CalledInAny
  public static VirtualFile getSelectedFile(@NotNull DataContext dataProvider) {
    FileEditor fileEditor = PlatformDataKeys.LAST_ACTIVE_FILE_EDITOR.getData(dataProvider);
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
    return workingTreeChangeStarted(project, null, null);
  }

  @NotNull
  public static AccessToken workingTreeChangeStarted(@NotNull Project project, @Nullable @NlsContexts.Label String activityName) {
    return workingTreeChangeStarted(project, activityName, null);
  }

  @NotNull
  public static AccessToken workingTreeChangeStarted(@NotNull Project project,
                                                     @Nullable @NlsContexts.Label String activityName,
                                                     @Nullable ActivityId activityId) {
    BackgroundTaskUtil.syncPublisher(BatchFileChangeListener.TOPIC).batchChangeStarted(project, activityName);
    LocalHistoryAction action = ObjectUtils.doIfNotNull(activityId, id -> {
      return LocalHistory.getInstance().startAction(activityName, id);
    });
    return new AccessToken() {
      @Override
      public void finish() {
        if (action != null) action.finish();
        BackgroundTaskUtil.syncPublisher(BatchFileChangeListener.TOPIC).batchChangeCompleted(project);
      }
    };
  }

  public static final Comparator<Repository> REPOSITORY_COMPARATOR =
    Comparator.comparing(DvcsUtil::getShortRepositoryName, NaturalComparator.INSTANCE);

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

  public static void addMappingIfSubRoot(@NotNull Project project,
                                         @NotNull @NonNls String newRepositoryPath,
                                         @NotNull @NonNls String vcsName) {
    if (!project.isDisposed() && project.getBasePath() != null && FileUtil.isAncestor(project.getBasePath(), newRepositoryPath, true)) {
      ProjectLevelVcsManager manager = ProjectLevelVcsManager.getInstance(project);
      manager.setDirectoryMappings(VcsUtil.addMapping(manager.getDirectoryMappings(), newRepositoryPath, vcsName));
    }
  }

  /**
   * Find the VCS root to use as a 'current' in a status bar widget.
   * <p>
   * Note: Do not call directly, use per-vcs utility method that provides correct {@code recentRootPath}.
   *
   * @param recentRootPath The last repository root that was shown in the widget,
   *                       see {@link com.intellij.dvcs.ui.DvcsStatusWidget#rememberRecentRoot(String)}.
   * @param selectedFile   The file in context, see {@link #getSelectedFile(Project)}.
   */
  @Nullable
  @CalledInAny
  public static <T extends Repository> T guessWidgetRepository(@NotNull Project project,
                                                               @NotNull AbstractRepositoryManager<T> manager,
                                                               @Nullable @NonNls @SystemIndependent String recentRootPath,
                                                               @Nullable VirtualFile selectedFile) {
    T repository = manager.getRepositoryForRootQuick(findVcsRootFor(project, selectedFile));
    if (repository != null) return repository;

    repository = manager.getRepositoryForRootQuick(guessRootForVcs(project, manager.getVcs(), recentRootPath));
    if (repository != null) return repository;

    return null;
  }

  @Nullable
  @CalledInAny
  public static <T extends Repository> T guessWidgetRepository(@NotNull Project project,
                                                               @NotNull AbstractRepositoryManager<T> manager,
                                                               @Nullable @NonNls @SystemIndependent String recentRootPath,
                                                               @NotNull DataContext dataContext) {
    VirtualFile file = getSelectedFile(dataContext);
    T repository = manager.getRepositoryForRootQuick(findVcsRootFor(project, file));
    if (repository != null) return repository;

    repository = manager.getRepositoryForRootQuick(guessRootForVcs(project, manager.getVcs(), recentRootPath));
    if (repository != null) return repository;

    return null;
  }

  /**
   * Find the VCS root on which a repository-wide AnAction is to be invoked in an unspecified context.
   * <p>
   * Prefer using {@link #guessRepositoryForOperation(Project, AbstractRepositoryManager, DataContext)} whenever possible.
   */
  @Nullable
  @RequiresEdt
  public static <T extends Repository> T guessRepositoryForOperation(@NotNull Project project,
                                                                     @NotNull AbstractRepositoryManager<T> manager) {
    DataContext dataContext = SimpleDataContext.builder()
      .add(CommonDataKeys.PROJECT, project)
      .add(PlatformDataKeys.LAST_ACTIVE_FILE_EDITOR, FileEditorManager.getInstance(project).getSelectedEditor())
      .build();

    return guessRepositoryForOperation(project, manager, dataContext);
  }

  /**
   * Find the VCS root on which a repository-wide AnAction is to be invoked in a given context.
   */
  @Nullable
  @CalledInAny
  public static <T extends Repository> T guessRepositoryForOperation(@NotNull Project project,
                                                                     @NotNull AbstractRepositoryManager<T> manager,
                                                                     @NotNull DataContext dataContext) {
    VirtualFile file = dataContext.getData(CommonDataKeys.VIRTUAL_FILE);
    T repository = manager.getRepositoryForRootQuick(findVcsRootFor(project, file));
    if (repository != null) return repository;

    file = getSelectedFile(dataContext); // last active FileEditor
    repository = manager.getRepositoryForRootQuick(findVcsRootFor(project, file));
    if (repository != null) return repository;

    repository = manager.getRepositoryForRootQuick(guessRootForVcs(project, manager.getVcs(), null));
    if (repository != null) return repository;

    return null;
  }

  /**
   * Find the VCS root on which a DVCS-generic repository-wide AnAction is to be invoked in a given context.
   */
  @Nullable
  @CalledInAny
  public static Repository guessRepositoryForOperation(@NotNull Project project,
                                                       @NotNull DataContext dataContext) {
    VcsRepositoryManager manager = VcsRepositoryManager.getInstance(project);

    VirtualFile file = dataContext.getData(CommonDataKeys.VIRTUAL_FILE);
    Repository repository = manager.getRepositoryForRootQuick(findVcsRootFor(project, file));
    if (repository != null) return repository;

    VirtualFile selectedFile = getSelectedFile(dataContext);
    repository = manager.getRepositoryForRootQuick(findVcsRootFor(project, selectedFile));
    if (repository != null) return repository;

    return null;
  }

  /**
   * @deprecated Prefer {@link #guessWidgetRepository} or {@link #guessRepositoryForOperation}.
   */
  @Nullable
  @Deprecated
  @CalledInAny
  public static <T extends Repository> T guessRepositoryForFile(@NotNull Project project,
                                                                @NotNull RepositoryManager<T> manager,
                                                                @Nullable VirtualFile file,
                                                                @Nullable @NonNls String defaultRootPathValue) {
    T repository = manager.getRepositoryForRootQuick(guessVcsRoot(project, file));
    if (repository != null) return repository;
    return manager.getRepositoryForRootQuick(guessRootForVcs(project, manager.getVcs(), defaultRootPathValue));
  }

  /**
   * @deprecated Prefer {@link #guessWidgetRepository} or {@link #guessRepositoryForOperation}.
   */
  @Nullable
  @Deprecated
  @RequiresEdt
  public static <T extends Repository> T guessCurrentRepositoryQuick(@NotNull Project project,
                                                                     @NotNull AbstractRepositoryManager<T> manager,
                                                                     @Nullable @NonNls String defaultRootPathValue) {
    T repository = manager.getRepositoryForRootQuick(guessVcsRoot(project, getSelectedFile(project)));
    if (repository != null) return repository;
    return manager.getRepositoryForRootQuick(guessRootForVcs(project, manager.getVcs(), defaultRootPathValue));
  }

  /**
   * Take configured VCS root in order:
   * 1) Matching 'defaultRootPathValue' path
   * 2) Matching {@link Project#getBaseDir()} or its ancestor
   * 3) Any (typically - first one by {@link NewMappings#MAPPINGS_COMPARATOR})
   */
  @Nullable
  private static VirtualFile guessRootForVcs(@NotNull Project project,
                                             @NotNull AbstractVcs vcs,
                                             @Nullable @NonNls String defaultRootPathValue) {
    if (project.isDisposed()) return null;
    LOG.debug("Guessing vcs root...");
    ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(project);
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
      if (ArrayUtil.contains(recentRoot, vcsRoots)) {
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
    return ContainerUtil.sorted(validRepositories, REPOSITORY_COMPARATOR);
  }

  /**
   * Check if passed file is a part of a project library, and find a relevant VCS mapping (ex: for its module).
   */
  @Nullable
  private static VirtualFile getVcsRootForLibraryFile(@NotNull Project project, @NotNull VirtualFile file) {
    ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(project);

    // For a file inside .jar/.zip, check VCS for the .jar/.zip file itself
    VirtualFile root = vcsManager.getVcsRootFor(VfsUtilCore.getVirtualFileForJar(file));
    if (root != null) {
      LOG.debug("Found root for zip/jar file: " + root);
      return root;
    }

    Set<VirtualFile> modulesVcsRoots = ReadAction.compute(() -> findVcsRootForModuleLibrary(project, file));
    if (modulesVcsRoots.isEmpty()) {
      LOG.debug("No library roots");
      return null;
    }

    // If the lib is used in several modules under different VCS roots, take the topmost one.
    // For modules not sharing ancestry, we can't guess anything => take the first one.
    VirtualFile topRoot = null;
    for (VirtualFile vcsRoot : modulesVcsRoots) {
      if (topRoot == null || VfsUtilCore.isAncestor(vcsRoot, topRoot, true)) {
        topRoot = vcsRoot;
      }
    }
    LOG.debug("Several library roots, returning " + topRoot);
    return topRoot;
  }

  /**
   * IJPL-95268 For libraries, check VCS for the owner module
   */
  private static Set<VirtualFile> findVcsRootForModuleLibrary(@NotNull Project project, @NotNull VirtualFile file) {
    ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(project);

    List<OrderEntry> entries = ProjectRootManager.getInstance(project).getFileIndex().getOrderEntriesForFile(file);
    Set<VirtualFile> modulesVcsRoots = new HashSet<>();
    for (OrderEntry entry : entries) {
      if (entry instanceof LibraryOrderEntry || entry instanceof JdkOrderEntry) {
        VirtualFile moduleVcsRoot = vcsManager.getVcsRootFor(entry.getOwnerModule().getModuleFile());
        if (moduleVcsRoot != null) {
          modulesVcsRoots.add(moduleVcsRoot);
        }
      }
    }
    return modulesVcsRoots;
  }

  /**
   * @deprecated Prefer {@link #findVcsRootFor}, {@link #guessWidgetRepository} or {@link #guessRepositoryForOperation}.
   */
  @Nullable
  @Deprecated(forRemoval = true)
  public static VirtualFile guessVcsRoot(@NotNull Project project, @Nullable VirtualFile file) {
    return findVcsRootFor(project, file);
  }

  /**
   * Find relevant VCS root for a given file, if any. Note that this root might not track the file itself.
   */
  @Nullable
  public static VirtualFile findVcsRootFor(@NotNull Project project, @Nullable VirtualFile file) {
    VirtualFile root = ProjectLevelVcsManager.getInstance(project).getVcsRootFor(file);
    if (root != null) return root;

    if (file != null) {
      root = getVcsRootForLibraryFile(project, file);
      if (root != null) return root;
    }

    return null;
  }

  @NotNull
  @RequiresBackgroundThread
  public static <R extends Repository> Map<R, List<VcsCommitMetadata>> groupCommitsByRoots(@NotNull RepositoryManager<R> repoManager,
                                                                                              @NotNull List<? extends VcsCommitMetadata> commits) {
    Map<R, List<VcsCommitMetadata>> groupedCommits = new HashMap<>();
    for (VcsCommitMetadata commit : commits) {
      R repository = repoManager.getRepositoryForRoot(commit.getRoot());
      if (repository == null) {
        LOG.info("No repository found for commit " + commit);
        continue;
      }
      List<VcsCommitMetadata> commitsInRoot = groupedCommits.computeIfAbsent(repository, __ -> new ArrayList<>());
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
    return VcsUtil.joinWithAnd(strings, limit);
  }
}
