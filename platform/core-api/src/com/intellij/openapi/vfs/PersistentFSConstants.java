// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.io.FileUtilRt;

public final class PersistentFSConstants {
  public static final long FILE_LENGTH_TO_CACHE_THRESHOLD = FileUtilRt.LARGE_FOR_CONTENT_LOADING;
  /**
   * always  in range [0, {@link #FILE_LENGTH_TO_CACHE_THRESHOLD}]
   */
  private static int ourMaxIntellisenseFileSize = Math.min(FileUtilRt.getUserFileSizeLimit(), (int)FILE_LENGTH_TO_CACHE_THRESHOLD);

  public static int getMaxIntellisenseFileSize() {
    return ourMaxIntellisenseFileSize;
  }

  public static void setMaxIntellisenseFileSize(int sizeInBytes) {
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      throw new IllegalStateException("cannot change max setMaxIntellisenseFileSize while running");
    }
    ourMaxIntellisenseFileSize = sizeInBytes;
  }

  private PersistentFSConstants() {
  }
}
