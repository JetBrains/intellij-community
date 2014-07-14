/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.jps.classFilesIndex.indexer.api;

import com.intellij.openapi.util.io.FileUtil;

import java.io.File;
import java.io.IOException;

/**
 * @author Dmitry Batkovich
 */
public enum IndexState {
  CORRUPTED,
  NOT_EXIST,
  EXIST;

  public static final String STATE_FILE_NAME = "state";

  public void save(final File indexDir) {
    try {
      FileUtil.writeToFile(new File(indexDir, STATE_FILE_NAME), name());
    }
    catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static IndexState load(final File indexDir) {
    try {
      final File indexStateFile = new File(indexDir, STATE_FILE_NAME);
      if (!indexStateFile.exists()) {
        NOT_EXIST.save(indexDir);
        return NOT_EXIST;
      }
      return Enum.valueOf(IndexState.class, FileUtil.loadFile(indexStateFile));
    }
    catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }
}
