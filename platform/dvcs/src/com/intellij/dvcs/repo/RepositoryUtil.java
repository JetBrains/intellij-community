package com.intellij.dvcs.repo;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.Callable;

/**
 * @author Nadya Zabrodina
 */
public class RepositoryUtil {

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
   *
   * @param file File to read.
   * @return file content.
   */
  @NotNull
  public static String tryLoadFile(final File file) {
    return tryOrThrow(new Callable<String>() {
      @Override
      public String call() throws Exception {
        return FileUtil.loadFile(file);
      }
    }, file);
  }

  /**
   * Tries to execute the given action.
   * If an IOException happens, tries again up to 3 times, and then throws a {@link RepoStateException}.
   * If an other exception happens, rethrows it as a {@link RepoStateException}.
   * In the case of success returns the result of the task execution.
   */
  public static String tryOrThrow(Callable<String> actionToTry, File fileToLoad) {
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

  public static void visitVcsDirVfs(@NotNull VirtualFile gitDir, @NotNull Collection<String> subDirs) {
    gitDir.getChildren();
    for (String subdir : subDirs) {
      VirtualFile dir = gitDir.findFileByRelativePath(subdir);
      // process recursively, because we need to visit all branches under refs/heads and refs/remotes
      visitAllChildrenRecursively(dir);
    }
  }

  public static void visitAllChildrenRecursively(@Nullable VirtualFile dir) {
    if (dir == null) {
      return;
    }
    VfsUtil.processFilesRecursively(dir, new Processor<VirtualFile>() {
      @Override
      public boolean process(VirtualFile virtualFile) {
        return true;
      }
    });
  }

  public static class Updater implements Consumer<Object> {
    private final Repository myRepository;

    public Updater(Repository repository) {
      myRepository = repository;
    }

    @Override
    public void consume(Object dummy) {
      myRepository.update();
    }
  }
}
