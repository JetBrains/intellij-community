// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.cache.model;

public class SystemOpsStatistic {
  private final long deletionSpeedBytesPerSec;
  private final long connectionSpeedBytesPerSec;
  private final long decompressionSpeedBytesPesSec;

  public SystemOpsStatistic(long connectionSpeed, long decompressionTime, long deletionTime, long fileSizeInBytes) {
    this.connectionSpeedBytesPerSec = fileSizeInBytes / connectionSpeed * 1000;
    this.deletionSpeedBytesPerSec = fileSizeInBytes / deletionTime * 1000;
    this.decompressionSpeedBytesPesSec = fileSizeInBytes / decompressionTime * 1000;
  }

  public long getDeletionSpeedBytesPerSec() {
    return deletionSpeedBytesPerSec;
  }

  public long getConnectionSpeedBytesPerSec() {
    return connectionSpeedBytesPerSec;
  }

  public long getDecompressionSpeedBytesPesSec() {
    return decompressionSpeedBytesPesSec;
  }
}
