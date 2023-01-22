// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.dependencies;

import com.google.common.io.MoreFiles;
import com.google.common.io.RecursiveDeleteOption;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Clean-up local download cache in two stages:
 * 1) mark old files by writing near them a marker file (.marked.for.cleanup)
 * 2) on the second run remove both marked and original old files.
 * <p>
 * Why two stage removing is required: suppose you're running some build scripts after a month of vacation.
 * Older downloaded files will be marked for deletion and then some of them will be used again.
 * Without the marking they would be removed and re-downloaded again, which we do not want.
 */
public final class BuildDependenciesDownloaderCleanup {
  private static final Logger LOG = Logger.getLogger(BuildDependenciesDownloaderCleanup.class.getName());

  private static final Duration MAXIMUM_ACCESS_TIME_AGE = Duration.ofDays(22);
  private static final Duration CLEANUP_EVERY_DURATION = Duration.ofDays(1);

  static final String LAST_CLEANUP_MARKER_FILE_NAME = ".last.cleanup.marker";
  private static final String MARKED_FOR_CLEANUP_SUFFIX = ".marked.for.cleanup";

  private final Path myCachesDir;
  private final Path myLastCleanupMarkerFile;

  public BuildDependenciesDownloaderCleanup(Path cachesDir) {
    myCachesDir = cachesDir;
    myLastCleanupMarkerFile = cachesDir.resolve(LAST_CLEANUP_MARKER_FILE_NAME);
  }

  public boolean runCleanupIfRequired() throws IOException {
    if (!isTimeForCleanup()) {
      return false;
    }

    // Update file timestamp mostly
    Files.writeString(myLastCleanupMarkerFile, LocalDateTime.now().toString());

    cleanupCachesDir();

    return true;
  }

  private boolean isTimeForCleanup() {
    try {
      return !Files.exists(myLastCleanupMarkerFile) ||
             Files.getLastModifiedTime(myLastCleanupMarkerFile).toMillis() < System.currentTimeMillis() - CLEANUP_EVERY_DURATION.toMillis();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void cleanupCachesDir() throws IOException {
    if (!Files.exists(myCachesDir)) {
      LOG.fine("Caches directory '" + myCachesDir + "' is missing, skipping cleanup");
      return;
    }

    if (!Files.isDirectory(myCachesDir)) {
      throw new IllegalStateException("Caches directory '" + myCachesDir + "' is not a directory");
    }

    Set<Path> cacheFiles = new HashSet<>(BuildDependenciesUtil.listDirectory(myCachesDir));

    long maxTimeMs = MAXIMUM_ACCESS_TIME_AGE.toMillis();
    long currentTime = System.currentTimeMillis();

    for (Path file : cacheFiles) {
      if (file.equals(myLastCleanupMarkerFile)) {
        continue;
      }

      String fileName = file.getFileName().toString();
      if (fileName.endsWith(MARKED_FOR_CLEANUP_SUFFIX)) {
        Path realFile = myCachesDir.resolve(fileName.substring(0, fileName.length() - MARKED_FOR_CLEANUP_SUFFIX.length()));
        if (!cacheFiles.contains(realFile)) {
          LOG.info("CACHE-CLEANUP: Removing orphan marker: " + file);
          Files.delete(file);
        }

        continue;
      }

      Path markFile = myCachesDir.resolve(fileName + MARKED_FOR_CLEANUP_SUFFIX);

      BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
      FileTime lastAccessTime = attrs.lastModifiedTime();
      if (lastAccessTime.toMillis() > currentTime - maxTimeMs) {
        if (cacheFiles.contains(markFile)) {
          // File was recently updated, un-mark it
          Files.delete(markFile);
        }
        continue;
      }

      if (Files.exists(markFile)) {
        // File is old AND already marked for cleanup - delete
        LOG.info("CACHE-CLEANUP: Deleting file/directory '" + file + "': it's too old and marked for cleanup");

        // Renaming file to a temporary name to prevent deletion of currently opened files, just in case
        Path toRemove = myCachesDir.resolve(fileName + ".toRemove." + UUID.randomUUID());

        try {
          Files.move(file, toRemove);
          MoreFiles.deleteRecursively(toRemove, RecursiveDeleteOption.ALLOW_INSECURE);
        }
        catch (Throwable t) {
          StringWriter writer = new StringWriter();
          t.printStackTrace(new PrintWriter(writer));

          LOG.warning("Unable to delete file '" + file + "': " + t.getMessage() + "\n" + writer);
        }

        Files.delete(markFile);
      }
      else {
        LOG.info("CACHE-CLEANUP: Marking File '" + file + "' for deletion, it'll be removed on the next cleanup run");
        Files.writeString(markFile, "");
      }
    }
  }
}
