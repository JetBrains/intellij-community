// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.compiler.cache.statistic;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.diagnostic.Logger;

public class JpsCacheLoadingStats {
  private static final Logger LOG = Logger.getInstance(JpsCacheLoadingStats.class);
  private static final String APPROXIMATE_DELETION_SPEED = "JpsOutputLoaderManager.deletionBytesPerSec";
  private static final String APPROXIMATE_DECOMPRESSION_SPEED = "JpsOutputLoaderManager.decompressionBytesPerSec";

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
