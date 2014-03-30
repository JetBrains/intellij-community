/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.vfs;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.io.FileUtilRt;

public class PersistentFSConstants {
  public static final long FILE_LENGTH_TO_CACHE_THRESHOLD = FileUtilRt.LARGE_FOR_CONTENT_LOADING;
  /**
   * always  in range [0, PersistentFS.FILE_LENGTH_TO_CACHE_THRESHOLD]
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
