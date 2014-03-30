package com.intellij.platform.templates.github;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.Producer;
import com.intellij.util.containers.Predicate;
import com.intellij.util.net.HttpConfigurable;
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

  /**
   * Downloads content of {@code url} to {@code outputFile} atomically.<br/>
   * {@code outputFile} isn't modified if an I/O error occurs or {@code contentChecker} is provided and returns false on the downloaded content.
   * More formally, the steps are:
   * <ol>
   *   <li>Download {@code url} to {@code tempFile}. Stop in case of any I/O errors.</li>
   *   <li>Stop if {@code contentChecker} is provided, and it returns false on the downloaded content.</li>
   *   <li>Move {@code tempFile} to {@code outputFile}. On most OS this operation is done atomically.</li>
   * </ol>
   *
   * Motivation: some web filtering products return pure HTML with HTTP 200 OK status instead of
   * the asked content.
   *
   * @param indicator   progress indicator
   * @param url         url to download
   * @param outputFile  output file
   * @param tempFile    temporary file to download to. This file is deleted on method exit.
   * @param contentChecker checks whether the downloaded content is OK or not
   * @returns true if no {@code contentChecker} is provided or the provided one returned true
   * @throws IOException if an I/O error occurs
   */
  public static boolean downloadAtomically(@Nullable ProgressIndicator indicator,
                                        @NotNull String url,
                                        @NotNull File outputFile,
                                        @NotNull File tempFile,
                                        @Nullable Predicate<String> contentChecker) throws IOException
  {
    try {
      downloadContentToFile(indicator, url, tempFile);
      if (contentChecker != null) {
        String content = FileUtil.loadFile(tempFile);
        if (!contentChecker.apply(content)) {
          return false;
        }
      }
      FileUtil.rename(tempFile, outputFile);
      return true;
    } finally {
      FileUtil.delete(tempFile);
    }
  }

  /**
   * Downloads content of {@code url} to {@code outputFile} atomically.
   * {@code outputFile} won't be modified in case of any I/O download errors.
   *
   * @param indicator   progress indicator
   * @param url         url to download
   * @param outputFile  output file
   */
  public static void downloadAtomically(@Nullable ProgressIndicator indicator,
                                        @NotNull String url,
                                        @NotNull File outputFile) throws IOException
  {
    File tempFile = FileUtil.createTempFile("for-actual-downloading-", null);
    downloadAtomically(indicator, url, outputFile, tempFile, null);
  }

  /**
   * Downloads content of {@code url} to {@code outputFile} atomically.
   * {@code outputFile} won't be modified in case of any I/O download errors.
   *
   * @param indicator   progress indicator
   * @param url         url to download
   * @param outputFile  output file
   * @param tempFile    temporary file to download to. This file is deleted on method exit.
   */
  public static void downloadAtomically(@Nullable ProgressIndicator indicator,
                                        @NotNull String url,
                                        @NotNull File outputFile,
                                        @NotNull File tempFile) throws IOException
  {
    downloadAtomically(indicator, url, outputFile, tempFile, null);
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
      throw new IOException(
        "Can not download '" + location
        + "', response code: " + urlConnection.getResponseCode()
        + ", response message: " + urlConnection.getResponseMessage()
        + ", headers: " + urlConnection.getHeaderFields(),
        e
      );
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
