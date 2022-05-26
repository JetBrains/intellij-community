// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.cache.statistics;

public class JpsCacheLoadingSystemStats {
  private static long decompressionSpeedBytesPesSec;
  private static long deletionSpeedBytesPerSec;

  public static long getDecompressionSpeedBytesPesSec() {
    return decompressionSpeedBytesPesSec;
  }

  public static void setDecompressionTimeMs(long fileSize, long duration) {
    decompressionSpeedBytesPesSec = fileSize / duration * 1000;
  }

  public static void setDecompressionSpeed(long decompressionSpeed) {
    if (decompressionSpeed > 0) decompressionSpeedBytesPesSec = decompressionSpeed;
  }

  public static long getDeletionSpeedBytesPerSec() {
    return deletionSpeedBytesPerSec;
  }

  public static void setDeletionTimeMs(long fileSize, long duration) {
    deletionSpeedBytesPerSec = fileSize / duration * 1000;
  }

  public static void setDeletionSpeed(long deletionSpeed) {
    if (deletionSpeed > 0) deletionSpeedBytesPerSec = deletionSpeed;
  }
}
