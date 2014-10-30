/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.Callable;

public class RepositoryUtil {

  public static final Comparator<Repository> REPOSITORY_COMPARATOR = new Comparator<Repository>() {
    @Override
    public int compare(Repository o1, Repository o2) {
      return o1.getPresentableUrl().compareTo(o2.getPresentableUrl());
    }
  };

  private static final Logger LOGGER = Logger.getInstance(RepositoryUtil.class);
  private static final int IO_RETRIES = 3; // number of retries before fail if an IOException happens during file read.

  public static void assertFileExists(File file, String message) {
    if (!file.exists()) {
      throw new RepoStateException(message);
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
  public static String tryLoadFile(@NotNull final File file) {
    return tryOrThrow(new Callable<String>() {
      @Override
      public String call() throws Exception {
        return StringUtil.convertLineSeparators(FileUtil.loadFile(file).trim());
      }
    }, file);
  }

  /**
   * Tries to execute the given action.
   * If an IOException happens, tries again up to 3 times, and then throws a {@link RepoStateException}.
   * If an other exception happens, rethrows it as a {@link RepoStateException}.
   * In the case of success returns the result of the task execution.
   */
  public static <T> T tryOrThrow(Callable<T> actionToTry, File fileToLoad) {
    IOException cause = null;
    for (int i = 0; i < IO_RETRIES; i++) {
      try {
        return actionToTry.call();
      }
      catch (IOException e) {
        LOGGER.info("IOException while loading " + fileToLoad, e);
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
    List<T> repos = ContainerUtil.filter(repositories, new Condition<T>() {
      @Override
      public boolean value(T t) {
        return t.getRoot().isValid();
      }
    });
    Collections.sort(repos, REPOSITORY_COMPARATOR);
    return repos;
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
    ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    if (file != null) {
      if (fileIndex.isInLibrarySource(file) || fileIndex.isInLibraryClasses(file)) {
        LOGGER.debug("File is in library sources " + file);
        root = getVcsRootForLibraryFile(project, file);
      }
      else {
        LOGGER.debug("File is not in library sources " + file);
        root = ProjectLevelVcsManager.getInstance(project).getVcsRootFor(file);
      }
    }
    return root;
  }
}
