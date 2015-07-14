/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.dvcs;

import com.intellij.dvcs.repo.RepoStateException;
import com.intellij.dvcs.repo.Repository;
import com.intellij.dvcs.repo.RepositoryManager;
import com.intellij.ide.file.BatchFileChangeListener;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.JdkOrderEntry;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.impl.status.StatusBarUtil;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.storage.HeavyProcessLatch;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.vcs.log.TimedVcsCommit;
import com.intellij.vcs.log.VcsFullCommitDetails;
import com.intellij.vcsUtil.VcsUtil;
import org.intellij.images.editor.ImageFileEditor;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.Callable;

public class DvcsUtil {

  private static final Logger LOG = Logger.getInstance(DvcsUtil.class);

  private static final Logger LOGGER = Logger.getInstance(DvcsUtil.class);
  private static final int IO_RETRIES = 3; // number of retries before fail if an IOException happens during file read.
  private static final int SHORT_HASH_LENGTH = 8;
  private static final int LONG_HASH_LENGTH = 40;

  @NotNull
  public static String getShortRepositoryName(@NotNull Project project, @NotNull VirtualFile root) {
    VirtualFile projectDir = project.getBaseDir();

    String repositoryPath = root.getPresentableUrl();
    if (projectDir != null) {
      String relativePath = VfsUtilCore.getRelativePath(root, projectDir, File.separatorChar);
      if (relativePath != null) {
        repositoryPath = relativePath;
      }
    }

    return repositoryPath.isEmpty() ? root.getName() : repositoryPath;
  }

  @NotNull
  public static String getShortRepositoryName(@NotNull Repository repository) {
    return getShortRepositoryName(repository.getProject(), repository.getRoot());
  }

  @NotNull
  public static String getShortNames(@NotNull Collection<? extends Repository> repositories) {
    return StringUtil.join(repositories, new Function<Repository, String>() {
      @Override
      public String fun(Repository repository) {
        return getShortRepositoryName(repository);
      }
    }, ", ");
  }

  public static boolean anyRepositoryIsFresh(Collection<? extends Repository> repositories) {
    for (Repository repository : repositories) {
      if (repository.isFresh()) {
        return true;
      }
    }
    return false;
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
    StatusBar statusBar = WindowManager.getInstance().getStatusBar(project);
    final FileEditor fileEditor = StatusBarUtil.getCurrentFileEditor(project, statusBar);
    VirtualFile result = null;
    if (fileEditor != null) {
      if (fileEditor instanceof TextEditor) {
        Document document = ((TextEditor)fileEditor).getEditor().getDocument();
        result = FileDocumentManager.getInstance().getFile(document);
      }
      else if (fileEditor instanceof ImageFileEditor) {
        result = ((ImageFileEditor)fileEditor).getImageEditor().getFile();
      }
    }

    if (result == null) {
      final FileEditorManager manager = FileEditorManager.getInstance(project);
      if (manager != null) {
        Editor editor = manager.getSelectedTextEditor();
        if (editor != null) {
          result = FileDocumentManager.getInstance().getFile(editor.getDocument());
        }
      }
    }
    return result;
  }

  @NotNull
  public static String getShortHash(@NotNull String hash) {
    if (hash.length() < SHORT_HASH_LENGTH) {
      LOG.debug("Unexpectedly short hash: [" + hash + "]");
    }
    if (hash.length() > LONG_HASH_LENGTH) {
      LOG.debug("Unexpectedly long hash: [" + hash + "]");
    }
    return hash.substring(0, Math.min(SHORT_HASH_LENGTH, hash.length()));
  }

  @NotNull
  public static String getDateString(@NotNull TimedVcsCommit commit) {
    return DateFormatUtil.formatPrettyDateTime(commit.getTimestamp()) + " ";
  }

  @NotNull
  public static AccessToken workingTreeChangeStarted(@NotNull Project project) {
    ApplicationManager.getApplication().getMessageBus().syncPublisher(BatchFileChangeListener.TOPIC).batchChangeStarted(project);
    return HeavyProcessLatch.INSTANCE.processStarted("Changing DVCS working tree");
  }

  public static void workingTreeChangeFinished(@NotNull Project project, @NotNull AccessToken token) {
    token.finish();
    ApplicationManager.getApplication().getMessageBus().syncPublisher(BatchFileChangeListener.TOPIC).batchChangeCompleted(project);
  }

  public static final Comparator<Repository> REPOSITORY_COMPARATOR = new Comparator<Repository>() {
    @Override
    public int compare(Repository o1, Repository o2) {
      return o1.getPresentableUrl().compareTo(o2.getPresentableUrl());
    }
  };

  public static void assertFileExists(File file, String message) throws IllegalStateException {
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
  @NotNull
  public static String tryLoadFile(@NotNull final File file) throws RepoStateException {
    return tryOrThrow(new Callable<String>() {
      @Override
      public String call() throws Exception {
        return StringUtil.convertLineSeparators(FileUtil.loadFile(file)).trim();
      }
    }, file);
  }

  @Nullable
  @Contract("_ , !null -> !null")
  public static String tryLoadFileOrReturn(@NotNull final File file, @Nullable String defaultValue) {
    try {
      return tryLoadFile(file);
    }
    catch (RepoStateException e) {
      LOG.error(e);
      return defaultValue;
    }
  }

  /**
   * Tries to execute the given action.
   * If an IOException happens, tries again up to 3 times, and then throws a {@link RepoStateException}.
   * If an other exception happens, rethrows it as a {@link RepoStateException}.
   * In the case of success returns the result of the task execution.
   */
  private static <T> T tryOrThrow(Callable<T> actionToTry, File fileToLoad) throws RepoStateException {
    IOException cause = null;
    for (int i = 0; i < IO_RETRIES; i++) {
      try {
        return actionToTry.call();
      }
      catch (IOException e) {
        LOG.info("IOException while loading " + fileToLoad, e);
        cause = e;
      }
      catch (Exception e) {    // this shouldn't happen since only IOExceptions are thrown in clients.
        throw new RepoStateException("Couldn't load file " + fileToLoad, e);
      }
    }
    throw new RepoStateException("Couldn't load file " + fileToLoad, cause);
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
      //noinspection unchecked
      VfsUtilCore.processFilesRecursively(dir, Processor.TRUE);
    }
  }

  public static void addMappingIfSubRoot(@NotNull Project project, @NotNull String newRepositoryPath, @NotNull String vcsName) {
    if (project.getBasePath() != null && FileUtil.isAncestor(project.getBasePath(), newRepositoryPath, true)) {
      ProjectLevelVcsManager manager = ProjectLevelVcsManager.getInstance(project);
      manager.setDirectoryMappings(VcsUtil.addMapping(manager.getDirectoryMappings(), newRepositoryPath, vcsName));
    }
  }

  public static class Updater implements Consumer<Object> {
    private final Repository myRepository;

    public Updater(Repository repository) {
      myRepository = repository;
    }

    @Override
    public void consume(Object dummy) {
      if (!Disposer.isDisposed(myRepository)) {
        myRepository.update();
      }
    }
  }

  public static <T extends Repository> List<T> sortRepositories(@NotNull Collection<T> repositories) {
    List<T> validRepositories = ContainerUtil.filter(repositories, new Condition<T>() {
      @Override
      public boolean value(T t) {
        return t.getRoot().isValid();
      }
    });
    Collections.sort(validRepositories, REPOSITORY_COMPARATOR);
    return validRepositories;
  }

  @Nullable
  private static VirtualFile getVcsRootForLibraryFile(@NotNull Project project, @NotNull VirtualFile file) {
    ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(project);
    // for a file inside .jar/.zip consider the .jar/.zip file itself
    VirtualFile root = vcsManager.getVcsRootFor(VfsUtilCore.getVirtualFileForJar(file));
    if (root != null) {
      LOGGER.debug("Found root for zip/jar file: " + root);
      return root;
    }

    // for other libs which don't have jars inside the project dir (such as JDK) take the owner module of the lib
    List<OrderEntry> entries = ProjectRootManager.getInstance(project).getFileIndex().getOrderEntriesForFile(file);
    Set<VirtualFile> libraryRoots = new HashSet<VirtualFile>();
    for (OrderEntry entry : entries) {
      if (entry instanceof LibraryOrderEntry || entry instanceof JdkOrderEntry) {
        VirtualFile moduleRoot = vcsManager.getVcsRootFor(entry.getOwnerModule().getModuleFile());
        if (moduleRoot != null) {
          libraryRoots.add(moduleRoot);
        }
      }
    }

    if (libraryRoots.size() == 0) {
      LOGGER.debug("No library roots");
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
    LOGGER.debug("Several library roots, returning " + topLibraryRoot);
    return topLibraryRoot;
  }

  @Nullable
  public static VirtualFile getVcsRoot(@NotNull Project project, @Nullable VirtualFile file) {
    VirtualFile root = null;
    if (file != null) {
      root = ProjectLevelVcsManager.getInstance(project).getVcsRootFor(file);
      if (root == null) {
        LOGGER.debug("Cannot get root by file. Trying with get by library: " + file);
        root = getVcsRootForLibraryFile(project, file);
      }
    }
    return root;
  }

  @NotNull
  public static <R extends Repository> Map<R, List<VcsFullCommitDetails>> groupCommitsByRoots(@NotNull RepositoryManager<R> repoManager,
                                                                                              @NotNull List<? extends VcsFullCommitDetails> commits) {
    Map<R, List<VcsFullCommitDetails>> groupedCommits = ContainerUtil.newHashMap();
    for (VcsFullCommitDetails commit : commits) {
      R repository = repoManager.getRepositoryForRoot(commit.getRoot());
      if (repository == null) {
        LOGGER.info("No repository found for commit " + commit);
        continue;
      }
      List<VcsFullCommitDetails> commitsInRoot = groupedCommits.get(repository);
      if (commitsInRoot == null) {
        commitsInRoot = ContainerUtil.newArrayList();
        groupedCommits.put(repository, commitsInRoot);
      }
      commitsInRoot.add(commit);
    }
    return groupedCommits;
  }
}
