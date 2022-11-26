// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.cache.loader;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.io.Decompressor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.JpsBuildBundle;
import org.jetbrains.jps.cache.JpsCachesLoaderUtil;
import org.jetbrains.jps.cache.client.JpsServerClient;
import org.jetbrains.jps.cache.model.JpsLoaderContext;
import org.jetbrains.jps.cache.statistics.JpsCacheLoadingSystemStats;
import org.jetbrains.jps.incremental.Utils;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

class JpsCacheLoader implements JpsOutputLoader<File> {
  private static final Logger LOG = Logger.getInstance(JpsCacheLoader.class);
  private static final String TIMESTAMPS_FOLDER_NAME = "timestamps";
  private static final String FS_STATE_FILE = "fs_state.dat";
  private final File myBuildCacheFolder;
  private final JpsServerClient myClient;
  private final boolean isCleanupAsynchronously;
  private JpsLoaderContext myContext;
  private File myTmpCacheFolder;

  JpsCacheLoader(@NotNull JpsServerClient client, @NotNull String myProjectPath, boolean cleanupAsynchronously) {
    myBuildCacheFolder = Utils.getDataStorageRoot(myProjectPath);
    myClient = client;
    isCleanupAsynchronously = cleanupAsynchronously;
  }

  @Nullable
  @Override
  public File load() {
    LOG.info("Loading JPS caches for commit: " + myContext.getCommitId());
    myTmpCacheFolder = null;

    long start = System.currentTimeMillis();
    File zipFile = myClient.downloadCacheById(myContext, myContext.getCommitId(), myBuildCacheFolder.getParentFile());
    LOG.info("Download of jps caches took: " + (System.currentTimeMillis() - start));
    return zipFile;
  }

  @Override
  public LoaderStatus extract(@Nullable Object loadResults) {
    if (!(loadResults instanceof File)) return LoaderStatus.FAILED;
    LOG.info("Start extraction of JPS caches");

    File zipFile = (File)loadResults;
    long fileSize = zipFile.length();
    File tmpFolder = new File(myBuildCacheFolder.getParentFile(), "tmp");
    try {
      // Start extracting after download
      myContext.sendDescriptionStatusMessage(JpsBuildBundle.message("progress.text.extracting.downloaded.results"));
      myContext.checkCanceled();

      long start = System.currentTimeMillis();
      AtomicInteger extractItemsCount = new AtomicInteger();
      new Decompressor.Zip(zipFile).postProcessor(path -> {
        extractItemsCount.incrementAndGet();
        myContext.checkCanceled();
        if (extractItemsCount.get() == 130) {
          int expectedDownloads = myContext.getTotalExpectedDownloads();
          myContext.getNettyClient().sendDescriptionStatusMessage(JpsBuildBundle.message("progress.details.extracting.project.caches"), expectedDownloads);
          extractItemsCount.set(0);
        }
      }).extract(tmpFolder.toPath());
      JpsCacheLoadingSystemStats.setDecompressionTimeMs(fileSize, System.currentTimeMillis() - start);

      long deletionStart = System.currentTimeMillis();
      FileUtil.delete(zipFile);
      JpsCacheLoadingSystemStats.setDeletionTimeMs(fileSize, System.currentTimeMillis() - deletionStart);
      LOG.info("Unzip compilation caches took: " + (System.currentTimeMillis() - start));

      myTmpCacheFolder = tmpFolder;
      return LoaderStatus.COMPLETE;
    }
    catch (ProcessCanceledException | IOException e) {
      if (e instanceof IOException) LOG.warn("Failed unzip downloaded compilation caches", e);
      FileUtil.delete(zipFile);
      FileUtil.delete(tmpFolder);
    }
    return LoaderStatus.FAILED;
  }

  @Override
  public void rollback() {
    if (myTmpCacheFolder != null && myTmpCacheFolder.exists()) {
      FileUtil.delete(myTmpCacheFolder);
      LOG.debug("JPS cache loader rolled back");
    }
  }

  @Override
  public void apply() {
    if (myTmpCacheFolder == null) {
      LOG.warn("Nothing to apply, download results are empty");
      return;
    }

    File newTimestampFolder = new File(myTmpCacheFolder, TIMESTAMPS_FOLDER_NAME);
    if (newTimestampFolder.exists()) FileUtil.delete(newTimestampFolder);

    myContext.sendDescriptionStatusMessage(JpsBuildBundle.message("progress.text.applying.jps.caches"));
    if (myBuildCacheFolder != null) {
      myContext.sendDescriptionStatusMessage(JpsBuildBundle.message("progress.details.applying.downloaded.caches"));
      // Copy timestamp old folder to new cache dir
      File timestamps = new File(myBuildCacheFolder, TIMESTAMPS_FOLDER_NAME);
      if (timestamps.exists()) {
        try {
          newTimestampFolder.mkdirs();
          FileUtil.copyDir(timestamps, newTimestampFolder);
        }
        catch (IOException e) {
          LOG.warn("Couldn't copy timestamps from old JPS cache", e);
        }
      }

      // Create new empty fsStateFile
      File fsStateFile = new File(myTmpCacheFolder, FS_STATE_FILE);
      fsStateFile.delete();
      try {
        fsStateFile.createNewFile();
      }
      catch (IOException e) {
        LOG.warn("Couldn't create new empty " + FS_STATE_FILE + " file", e);
      }

      // Remove old cache dir
      JpsCachesLoaderUtil.delete(myBuildCacheFolder, isCleanupAsynchronously);
      myTmpCacheFolder.renameTo(myBuildCacheFolder);
      //subTaskIndicator.finished();
      LOG.debug("JPS cache downloads finished");
    }
  }

  @Override
  public void setContext(@NotNull JpsLoaderContext context) {
    myContext = context;
  }
}