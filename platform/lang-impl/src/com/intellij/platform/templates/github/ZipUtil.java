package com.intellij.platform.templates.github;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.NullableFunction;
import com.intellij.util.Producer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * @author Sergey Simonchik
 */
public class ZipUtil {

  private static final Logger LOG = Logger.getInstance(ZipUtil.class);

  public static void unzipWithProgressSynchronously(
    @Nullable Project project,
    @NotNull String progressTitle,
    @NotNull final File zipArchive,
    @NotNull final File extractToDir) throws GeneratorException
  {
    Outcome<Boolean> outcome = DownloadUtil.provideDataWithProgressSynchronously(
      project, progressTitle, "Unpacking ...",
      new Callable<Boolean>() {
        @Override
        public Boolean call() throws IOException {
          ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
          ZipInputStream stream = new ZipInputStream(new FileInputStream(zipArchive));
          unzip(progress, extractToDir, stream, null);
          return true;
        }
      },
      new Producer<Boolean>() {
        @Override
        public Boolean produce() {
          return false;
        }
      }
    );
    Boolean result = outcome.get();
    if (result == null) {
      @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
      Exception e = outcome.getException();
      if (e != null) {
        throw new GeneratorException("Unpacking failed, downloaded archive is broken");
      }
      throw new GeneratorException("Unpacking was cancelled");
    }
  }

  public static void unzip(@Nullable ProgressIndicator progress,
                           File extractToDir,
                           ZipInputStream stream,
                           @Nullable NullableFunction<String, String> pathConvertor) throws IOException {
    if (progress != null) {
      progress.setText("Extracting...");
    }
    try {
      ZipEntry entry;
      while ((entry = stream.getNextEntry()) != null) {
        unzipEntryToDir(progress, entry, extractToDir, stream, pathConvertor);
      }
    } finally {
      stream.close();
    }
  }

  private static void unzipEntryToDir(@Nullable ProgressIndicator progress,
                                      @NotNull final ZipEntry zipEntry,
                                      @NotNull final File extractToDir,
                                      ZipInputStream stream,
                                      @Nullable NullableFunction<String, String> pathConvertor) throws IOException {

    String relativeExtractPath = createRelativeExtractPath(zipEntry);
    if (pathConvertor != null) {
      relativeExtractPath = pathConvertor.fun(relativeExtractPath);
      if (relativeExtractPath == null) {
        // should be skipped
        return;
      }
    }
    File child = new File(extractToDir, relativeExtractPath);
    File dir = zipEntry.isDirectory() ? child : child.getParentFile();
    if (!dir.exists() && !dir.mkdirs()) {
      throw new IOException("Unable to create dir: '" + dir + "'!");
    }
    if (zipEntry.isDirectory()) {
      return;
    }
    if (progress != null) {
      progress.setText("Extracting " + relativeExtractPath + " ...");
    }
    FileOutputStream fileOutputStream = new FileOutputStream(child);
    try {
      FileUtil.copy(stream, fileOutputStream);
    } finally {
      fileOutputStream.close();
    }
    LOG.info("Extract: " + relativeExtractPath);
  }

  private static String createRelativeExtractPath(ZipEntry zipEntry) {
    String name = zipEntry.getName();
    int ind = name.indexOf('/');
    if (ind >= 0) {
      name = name.substring(ind + 1);
    }
    return StringUtil.trimEnd(name, "/");
  }

}
