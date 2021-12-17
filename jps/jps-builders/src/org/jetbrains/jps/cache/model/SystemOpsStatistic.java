// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.cache.model;

public class SystemOpsStatistic {
  private final long deletionSpeedBytesPerSec;
  private final long connectionSpeedBytesPerSec;
  private final long decompressionSpeedBytesPesSec;

  public SystemOpsStatistic(long connectionSpeed, long decompressionTime, long deletionTime) {
    this.connectionSpeedBytesPerSec = connectionSpeed;
    this.deletionSpeedBytesPerSec = deletionTime;
    this.decompressionSpeedBytesPesSec = decompressionTime;
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
