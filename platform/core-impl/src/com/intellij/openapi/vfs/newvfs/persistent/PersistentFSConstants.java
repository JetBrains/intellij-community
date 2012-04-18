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
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NonNls;

public class PersistentFSConstants {
  public static final long FILE_LENGTH_TO_CACHE_THRESHOLD = 20 * 1024 * 1024; // 20 megabytes
  /**
   * always  in range [0, PersistentFS.FILE_LENGTH_TO_CACHE_THRESHOLD]
   */
  private static int ourMaxIntellisenseFilesize = computeMaxIntellisenseFileSize();
  @NonNls private static final String MAX_INTELLISENSE_SIZE_PROPERTY = "idea.max.intellisense.filesize";

  public static int getMaxIntellisenseFileSize() {
    return ourMaxIntellisenseFilesize;
  }

  public static void setMaxIntellisenseFileSize(int sizeInBytes) {
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      throw new IllegalStateException("cannot change max setMaxIntellisenseFileSize while running");
    }
    ourMaxIntellisenseFilesize = sizeInBytes;
  }

  private PersistentFSConstants() {
  }

  private static int computeMaxIntellisenseFileSize() {
    final int maxLimitBytes = (int)FILE_LENGTH_TO_CACHE_THRESHOLD;
    final String userLimitKb = System.getProperty(MAX_INTELLISENSE_SIZE_PROPERTY);
    try {
      return userLimitKb != null ? Math.min(Integer.parseInt(userLimitKb) * 1024, maxLimitBytes) : maxLimitBytes;
    }
    catch (NumberFormatException ignored) {
      return maxLimitBytes;
    }
  }
}
