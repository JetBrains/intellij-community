package com.intellij.platform.templates.github;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Producer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.concurrent.Callable;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * @author Sergey Simonchik
 */
class ZipUtil {

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
          unzip(progress, zipArchive, extractToDir);
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

  private static void unzip(@Nullable ProgressIndicator progress,
                            @NotNull File zipArchiveFile,
                            @NotNull File extractToDir) throws IOException {
    if (progress != null) {
      progress.setText("Extracting...");
    }
    ZipFile zipFile = new ZipFile(zipArchiveFile);
    try {
      boolean singleTopLevelDir = isSingleTopLevelDir(zipFile.entries());
      for (Enumeration<? extends ZipEntry> e = zipFile.entries(); e.hasMoreElements();) {
        ZipEntry zipEntry = e.nextElement();
        unzipEntryToDir(progress, zipFile, zipEntry, extractToDir, singleTopLevelDir);
      }
    } finally {
      zipFile.close();
    }
  }

  private static boolean isSingleTopLevelDir(@NotNull Enumeration<? extends ZipEntry> zipEntries) {
    String singleTopLevelDirName = null;
    char pathDelimiterChar = '/';
    int cnt = 0;
    while (zipEntries.hasMoreElements()) {
      cnt++;
      ZipEntry zipEntry = zipEntries.nextElement();
      String name = zipEntry.getName();
      int ind = (name + pathDelimiterChar).indexOf(pathDelimiterChar);
      String topLevelFileName = name.substring(0, ind);
      if (singleTopLevelDirName == null) {
        singleTopLevelDirName = topLevelFileName;
      }
      else if (!singleTopLevelDirName.equals(topLevelFileName)) {
        return false;
      }
    }
    return cnt > 0;
  }

  private static void unzipEntryToDir(@Nullable ProgressIndicator progress,
                                      @NotNull final ZipFile zipFile,
                                      @NotNull final ZipEntry zipEntry,
                                      @NotNull final File extractToDir,
                                      boolean singleTopLevelDir) throws IOException {
    final char pathDelimiterChar = '/';
    String relativeExtractPath = createRelativeExtractPath(zipEntry, pathDelimiterChar, singleTopLevelDir);
    int ind = relativeExtractPath.lastIndexOf(pathDelimiterChar);
    final String relativeParentDir;
    final String name;
    if (ind != -1) {
      relativeParentDir = relativeExtractPath.substring(0, ind);
      name = relativeExtractPath.substring(ind + 1);
    } else {
      relativeParentDir = "";
      name = relativeExtractPath;
    }
    if (zipEntry.isDirectory()) {
      return;
    }
    if (progress != null) {
      progress.setText("Extracting " + relativeExtractPath + " ...");
    }
    File parentDir = new File(extractToDir, relativeParentDir);
    if (!parentDir.exists() || !parentDir.isDirectory()) {
      boolean created = parentDir.mkdirs();
      if (!created) {
        throw new RuntimeException("Unable to create dir: '" + parentDir + "'!");
      }
    }
    File child = new File(parentDir, name);
    InputStream stream = zipFile.getInputStream(zipEntry);
    FileOutputStream fileOutputStream = new FileOutputStream(child);
    try {
      FileUtil.copy(stream, fileOutputStream);
    } finally {
      fileOutputStream.close();
      stream.close();
    }
    LOG.info("Extract: " + relativeExtractPath);
  }

  private static String createRelativeExtractPath(ZipEntry zipEntry, char pathDelimiterChar, boolean singleTopLevelDir) {
    String name = zipEntry.getName();
    if (singleTopLevelDir) {
      int ind = name.indexOf(pathDelimiterChar);
      if (ind >= 0) {
        name = name.substring(ind + 1);
      }
    }
    return StringUtil.trimEnd(name, String.valueOf(pathDelimiterChar));
  }

}
