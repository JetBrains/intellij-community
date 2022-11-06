// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.cache;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.diagnostic.Logger;

public class CompilerCacheLoadingSettings {
  private static final Logger LOG = Logger.getInstance(CompilerCacheLoadingSettings.class);

  private static final String FORCE_UPDATE = "JpsOutputLoaderManager.forceUpdate";
  private static final String DISABLE_UPDATE = "JpsOutputLoaderManager.disableUpdate";
  private static final String CLEANUP_ASYNCHRONOUSLY = "JpsOutputLoaderManager.cleanupAsynchronously";
  private static final String MAX_DOWNLOAD_DURATION = "JpsOutputLoaderManager.maxDownloadDuration";
  private static final String APPROXIMATE_DELETION_SPEED = "JpsOutputLoaderManager.deletionBytesPerSec";
  private static final String APPROXIMATE_DECOMPRESSION_SPEED = "JpsOutputLoaderManager.decompressionBytesPerSec";

  public static void saveForceUpdateValue(boolean forceUpdate) {
    PropertiesComponent.getInstance().setValue(FORCE_UPDATE, forceUpdate);
    LOG.info("Saving force update value: " + forceUpdate);
  }

  public static boolean getForceUpdateValue() {
    boolean forceUpdate = PropertiesComponent.getInstance().getBoolean(FORCE_UPDATE, false);
    LOG.info("Getting force update value: " + forceUpdate);
    return forceUpdate;
  }

  public static void saveDisableUpdateValue(boolean disableUpdate) {
    PropertiesComponent.getInstance().setValue(DISABLE_UPDATE, disableUpdate);
    LOG.info("Saving disable update value: " + disableUpdate);
  }

  public static boolean getDisableUpdateValue() {
    boolean disableUpdate = PropertiesComponent.getInstance().getBoolean(DISABLE_UPDATE, false);
    LOG.info("Getting disable update value: " + disableUpdate);
    return disableUpdate;
  }

  public static void saveCleanupAsynchronouslyValue(boolean cleanupAsynchronously) {
    PropertiesComponent.getInstance().setValue(CLEANUP_ASYNCHRONOUSLY, cleanupAsynchronously);
    LOG.info("Saving cleanup asynchronously value: " + cleanupAsynchronously);
  }

  public static boolean getCleanupAsynchronouslyValue() {
    boolean cleanupAsynchronously = PropertiesComponent.getInstance().getBoolean(CLEANUP_ASYNCHRONOUSLY, false);
    LOG.info("Getting cleanup asynchronously value: " + cleanupAsynchronously);
    return cleanupAsynchronously;
  }

  public static void saveMaxDownloadDuration(int maxDownloadDuration) {
    PropertiesComponent.getInstance().setValue(MAX_DOWNLOAD_DURATION, String.valueOf(maxDownloadDuration));
    LOG.info("Saving max download duration: " + maxDownloadDuration);
  }

  public static int getMaxDownloadDuration() {
    int maxDownloadDuration = PropertiesComponent.getInstance().getInt(MAX_DOWNLOAD_DURATION, 10);
    LOG.info("Getting max download duration: " + maxDownloadDuration);
    return maxDownloadDuration;
  }

  public static void saveApproximateDeletionSpeed(long deletionSpeed) {
    if (deletionSpeed == 0) {
      LOG.info("Deletion speed has default value and will be skipped");
      return;
    }
    PropertiesComponent.getInstance().setValue(APPROXIMATE_DELETION_SPEED, String.valueOf(deletionSpeed));
    LOG.info("Saving approximate deletion speed: " + deletionSpeed);
  }

  public static long getApproximateDeletionSpeed() {
    long deletionSpeed = PropertiesComponent.getInstance().getLong(APPROXIMATE_DELETION_SPEED, 0);
    LOG.info("Getting approximate deletion speed: " + deletionSpeed);
    return deletionSpeed;
  }

  public static void saveApproximateDecompressionSpeed(long decompressionSpeed) {
    if (decompressionSpeed == 0) {
      LOG.info("Decompression speed has default value and will be skipped");
      return;
    }
    PropertiesComponent.getInstance().setValue(APPROXIMATE_DECOMPRESSION_SPEED, String.valueOf(decompressionSpeed));
    LOG.info("Saving approximate decompression speed: " + decompressionSpeed);
  }

  public static long getApproximateDecompressionSpeed() {
    long decompressionSpeed = PropertiesComponent.getInstance().getLong(APPROXIMATE_DECOMPRESSION_SPEED, 0);
    LOG.info("Getting approximate decompression speed: " + decompressionSpeed);
    return decompressionSpeed;
  }
}
