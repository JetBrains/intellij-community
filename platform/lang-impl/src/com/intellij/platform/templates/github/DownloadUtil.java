package com.intellij.platform.templates.github;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.Producer;
import com.intellij.util.net.HttpConfigurable;
import com.intellij.util.net.IOExceptionDialog;
import com.intellij.util.net.NetUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.net.HttpURLConnection;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * @author Sergey Simonchik
 */
public class DownloadUtil {

  public static final String CONTENT_LENGTH_TEMPLATE = "${content-length}";
  private static final Logger LOG = Logger.getInstance(DownloadUtil.class);

  @NotNull
  private static String formatGithubRepositoryName(@Nullable String userName, @NotNull String repositoryName) {
    StringBuilder builder = new StringBuilder("github-");
    if (userName != null) {
      builder.append(userName).append("-");
    }
    return builder.append(repositoryName).toString();
  }

  private static File getCacheDir(@Nullable String userName, @NotNull String repositoryName) {
    File generatorsDir = new File(PathManager.getSystemPath(), "projectGenerators");
    String dirName = formatGithubRepositoryName(userName, repositoryName);
    File dir = new File(generatorsDir, dirName);
    try {
      return dir.getCanonicalFile();
    } catch (IOException e) {
      return dir;
    }
  }

  public static File findCacheFile(@Nullable String userName, @NotNull String repositoryName, @NotNull String cacheFileName) {
    File dir = getCacheDir(userName, repositoryName);
    return new File(dir, cacheFileName);
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
                                        @Nullable String userName,
                                        @NotNull String repositoryName) throws IOException
  {
    String fileName = new File(repositoryName).getName();
    String tempFileName = String.format("github-%s-%s-%s", userName, fileName, outputFile.getName());
    File tempFile = FileUtil.createTempFile(tempFileName + "-", ".tmp");
    downloadAtomically(indicator, url, outputFile, tempFile);
  }

  /**
   * Downloads content of {@code url} to {@code outputFile}.
   * {@code outputFile} won't be modified in case of any I/O download errors.
   *
   * @param indicator   progress indicator
   * @param url         url to download
   * @param outputFile  output file
   * @param tempFile    temporary file to download to
   */
  public static void downloadAtomically(@Nullable ProgressIndicator indicator,
                                        @NotNull String url,
                                        @NotNull File outputFile,
                                        @NotNull File tempFile) throws IOException
  {
    try {
      downloadContentToFile(indicator, url, tempFile);
      FileUtil.copy(tempFile, outputFile);
    } finally {
      FileUtil.delete(tempFile);
    }
  }


  @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
  public static void downloadContentToFileWithProgressSynchronously(
    @Nullable Project project,
    @NotNull final String url,
    @NotNull String progressTitle,
    @NotNull final File outputFile,
    @Nullable final String userName,
    @NotNull final String repositoryName) throws GeneratorException
  {
    Outcome<File> outcome = provideDataWithProgressSynchronously(
      project,
      progressTitle,
      "Downloading zip archive" + CONTENT_LENGTH_TEMPLATE + " ...",
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

  @NotNull
  public static <V> Outcome<V> provideDataWithProgressSynchronously(
    @Nullable Project project,
    @NotNull String progressTitle,
    @NotNull final String actionShortDescription,
    @NotNull final Callable<V> supplier,
    @Nullable Producer<Boolean> tryAgainProvider)
  {
    int attemptNumber = 1;
    while (true) {
      final Ref<V> dataRef = Ref.create(null);
      final Ref<Exception> innerExceptionRef = Ref.create(null);
      boolean completed = ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
        @Override
        public void run() {
          ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
          indicator.setText(actionShortDescription);
          try {
            V data = supplier.call();
            dataRef.set(data);
          }
          catch (Exception ex) {
            innerExceptionRef.set(ex);
          }
        }
      }, progressTitle, true, project);
      if (!completed) {
        return Outcome.createAsCancelled();
      }
      Exception latestInnerException = innerExceptionRef.get();
      if (latestInnerException == null) {
        return Outcome.createNormal(dataRef.get());
      }
      LOG.warn("[attempt#" + attemptNumber + "] Can not '" + actionShortDescription + "'", latestInnerException);
      boolean onceMore = false;
      if (tryAgainProvider != null) {
        onceMore = Boolean.TRUE.equals(tryAgainProvider.produce());
      }
      if (!onceMore) {
        return Outcome.createAsException(latestInnerException);
      }
      attemptNumber++;
    }
  }

  public static void downloadContentToFile(@Nullable ProgressIndicator progress,
                                           @NotNull String url,
                                           @NotNull File outputFile) throws IOException {
    boolean parentDirExists = FileUtil.createParentDirs(outputFile);
    if (!parentDirExists) {
      throw new IOException("Parent dir of '" + outputFile.getAbsolutePath() + "' can not be created!");
    }
    OutputStream out = new FileOutputStream(outputFile);
    try {
      download(progress, url, out);
    } finally {
      out.close();
    }
  }

  private static void download(@Nullable ProgressIndicator progress,
                               @NotNull String location,
                               @NotNull OutputStream output) throws IOException {
    String originalText = progress != null ? progress.getText() : null;
    substituteContentLength(progress, originalText, -1);
    if (progress != null) {
      progress.setText2("Downloading " + location);
    }
    HttpURLConnection urlConnection = HttpConfigurable.getInstance().openHttpConnection(location);
    try {
      int timeout = (int) TimeUnit.MINUTES.toMillis(2);
      urlConnection.setConnectTimeout(timeout);
      urlConnection.setReadTimeout(timeout);
      urlConnection.connect();
      InputStream in = urlConnection.getInputStream();
      int contentLength = urlConnection.getContentLength();
      substituteContentLength(progress, originalText, contentLength);
      NetUtils.copyStreamContent(progress, in, output, contentLength);
    } catch (IOException e) {
      LOG.warn("Can not download '" + location
               + "', response code: " + urlConnection.getResponseCode()
               + ", response message: " + urlConnection.getResponseMessage()
               + ", headers: " + urlConnection.getHeaderFields(),
               e
      );
      throw e;
    }
    finally {
      try {
        urlConnection.disconnect();
      } catch (Exception e) {
        LOG.warn("Exception at disconnect()", e);
      }
    }
  }

  private static void substituteContentLength(@Nullable ProgressIndicator progress, @Nullable String text, int contentLengthInBytes) {
    if (progress != null && text != null) {
      int ind = text.indexOf(CONTENT_LENGTH_TEMPLATE);
      if (ind != -1) {
        String mes = formatContentLength(contentLengthInBytes);
        String newText = text.substring(0, ind) + mes + text.substring(ind + CONTENT_LENGTH_TEMPLATE.length());
        progress.setText(newText);
      }
    }
  }

  private static String formatContentLength(int contentLengthInBytes) {
    if (contentLengthInBytes < 0) {
      return "";
    }
    final int kilo = 1024;
    if (contentLengthInBytes < kilo) {
      return ", " + contentLengthInBytes + " bytes";
    }
    if (contentLengthInBytes < kilo * kilo) {
      return String.format(Locale.US, ", %.1f kB", contentLengthInBytes / (1.0 * kilo));
    }
    return String.format(Locale.US, ", %.1f MB", contentLengthInBytes / (1.0 * kilo * kilo));
  }

}
