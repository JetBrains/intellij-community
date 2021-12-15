// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.cache.model;

public class SystemOpsStatistic {
  private final long deletionTimeBytesPerSec;
  private final long connectionSpeedBytesPerSec;
  private final long decompressionTimeBytesPesSec;

  public SystemOpsStatistic(long connectionSpeed, long decompressionTime, long deletionTime, long fileSizeInBytes) {
    this.connectionSpeedBytesPerSec = fileSizeInBytes / connectionSpeed * 1000;
    this.deletionTimeBytesPerSec = fileSizeInBytes / deletionTime * 1000;
    this.decompressionTimeBytesPesSec = fileSizeInBytes / decompressionTime * 1000;
  }

  public long getDeletionTimeBytesPerSec() {
    return deletionTimeBytesPerSec;
  }

  public long getConnectionSpeedBytesPerSec() {
    return connectionSpeedBytesPerSec;
  }

  public long getDecompressionTimeBytesPesSec() {
    return decompressionTimeBytesPesSec;
  }
}
