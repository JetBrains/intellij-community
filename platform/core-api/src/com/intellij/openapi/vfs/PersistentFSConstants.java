// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.io.FileUtilRt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.TestOnly;

import static com.intellij.util.SystemProperties.getIntProperty;

public final class PersistentFSConstants {

  /**
   * Max file size to cache inside VFS.
   * Cache huge files is rarely useful, but time-consuming (freeze-prone), and could quickly overflow VFS content storage capacity
   */
  @ApiStatus.Internal
  public static final int MAX_FILE_LENGTH_TO_CACHE = getIntProperty(
    "idea.vfs.max-file-length-to-cache",
    FileUtilRt.MEGABYTE
  );

  /**
   * Must always be in range [0, {@link FileUtilRt#LARGE_FOR_CONTENT_LOADING}]
   * <p>
   * Currently, this is always true, because
   * <pre>LARGE_FOR_CONTENT_LOADING = ... = max(20Mb, userFileSizeLimit, userContentLoadLimit)</pre>
   * but could be changed in the future, hence .min(...) here is to ensure that.
   * TODO: move into FileSizeLimit
   */
  private static int ourMaxIntellisenseFileSize = Math.min(FileUtilRt.getUserFileSizeLimit(), FileUtilRt.LARGE_FOR_CONTENT_LOADING);

  /** @deprecated Prefer using {@link com.intellij.openapi.vfs.limits.FileSizeLimit#getIntellisenseLimit(String)} */
  //TODO: move into FileSizeLimit
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
