// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.limits.FileSizeLimit;
import org.jetbrains.annotations.TestOnly;

public final class PersistentFSConstants {

  /** Max file size to cache inside VFS */
  public static final long FILE_LENGTH_TO_CACHE_THRESHOLD = FileSizeLimit.getFileLengthToCacheThreshold();

  /**
   * Must always be in range [0, {@link #FILE_LENGTH_TO_CACHE_THRESHOLD}]
   * <p>
   * Currently, this is always true, because
   * <pre>FILE_LENGTH_TO_CACHE_THRESHOLD = ... = max(20Mb, userFileSizeLimit, userContentLoadLimit)</pre>
   * but could be changed in the future, hence .min(...) here is to ensure that.
   */
  private static int ourMaxIntellisenseFileSize = Math.min(FileUtilRt.getUserFileSizeLimit(), (int)FILE_LENGTH_TO_CACHE_THRESHOLD);

  /** @deprecated Prefer using {@link com.intellij.openapi.vfs.limits.FileSizeLimit#getIntellisenseLimit(String)} */
  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated()
  public static int getMaxIntellisenseFileSize() {
    return ourMaxIntellisenseFileSize;
  }

  @TestOnly
  public static void setMaxIntellisenseFileSize(int sizeInBytes) {
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      throw new IllegalStateException("maxIntellisenseFileSize could be changed only in tests");
    }
    ourMaxIntellisenseFileSize = sizeInBytes;
  }

  private PersistentFSConstants() {
  }
}
