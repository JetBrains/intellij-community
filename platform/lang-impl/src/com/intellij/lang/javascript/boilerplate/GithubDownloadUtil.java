package com.intellij.lang.javascript.boilerplate;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.platform.templates.github.DownloadUtil;
import com.intellij.platform.templates.github.GeneratorException;
import com.intellij.platform.templates.github.Outcome;
import com.intellij.util.Producer;
import com.intellij.util.net.IOExceptionDialog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Callable;

/**
 * @author Sergey Simonchik
 */
public class GithubDownloadUtil {

  private GithubDownloadUtil() {}

  @NotNull
  private static String formatGithubRepositoryName(@NotNull String userName, @NotNull String repositoryName) {
    return "github-" + userName + "-" + repositoryName;
  }

  @NotNull
  public static File getCacheDir(@NotNull String userName, @NotNull String repositoryName) {
    File generatorsDir = new File(PathManager.getSystemPath(), "projectGenerators");
    String dirName = formatGithubRepositoryName(userName, repositoryName);
    File dir = new File(generatorsDir, dirName);
    try {
      return dir.getCanonicalFile();
    } catch (IOException e) {
      return dir;
    }
  }

  public static File findCacheFile(@NotNull String userName, @NotNull String repositoryName, @NotNull String cacheFileName) {
    File dir = getCacheDir(userName, repositoryName);
    return new File(dir, cacheFileName);
  }

  @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
  public static void downloadContentToFileWithProgressSynchronously(
    @Nullable Project project,
    @NotNull final String url,
    @NotNull String progressTitle,
    @NotNull final File outputFile,
    @NotNull final String userName,
    @NotNull final String repositoryName,
    final boolean retryOnError) throws GeneratorException
  {
    Outcome<File> outcome = DownloadUtil.provideDataWithProgressSynchronously(
      project,
      progressTitle,
      "Downloading zip archive" + DownloadUtil.CONTENT_LENGTH_TEMPLATE + " ...",
      new Callable<File>() {
        @Override
        public File call() throws Exception {
          ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
          downloadAtomically(progress, url, outputFile, userName, repositoryName);
          return outputFile;
        }
      }, new Producer<Boolean>() {
        @Override
        public Boolean produce() {
          if (!retryOnError) {
            return false;
          }
          return IOExceptionDialog.showErrorDialog("Download Error", "Can not download '" + url + "'");
        }
      }
    );
    File out = outcome.get();
    if (out != null) {
      return;
    }
    Exception e = outcome.getException();
    if (e != null) {
      throw new GeneratorException("Can not fetch content from " + url);
    }
    throw new GeneratorException("Download was cancelled");
  }

  /**
   * Downloads content of {@code url} to {@code outputFile}.
   * {@code outputFile} won't be modified in case of any I/O download errors.
   *
   * @param indicator   progress indicator
   * @param url         url to download
   * @param outputFile  output file
   */
  public static void downloadAtomically(@Nullable ProgressIndicator indicator,
                                        @NotNull String url,
                                        @NotNull File outputFile,
                                        @NotNull String userName,
                                        @NotNull String repositoryName) throws IOException
  {
    String tempFileName = String.format("github-%s-%s-%s", userName, repositoryName, outputFile.getName());
    File tempFile = FileUtil.createTempFile(tempFileName + "-", ".tmp");
    DownloadUtil.downloadAtomically(indicator, url, outputFile, tempFile);
  }

}
