/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.psi.impl.include;

import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
 */
public class FileIncludeInfo {

  public final String fileName;
  public final String path;
  public final int offset;
  public final boolean runtimeOnly;

  public FileIncludeInfo(@NotNull String fileName, @NotNull String path, int offset, boolean runtimeOnly) {
    this.fileName = fileName;
    this.path = path;
    this.offset = offset;
    this.runtimeOnly = runtimeOnly;
  }

  public FileIncludeInfo(@NotNull String path, int offset) {
    this(getFileName(path), path, offset, false);
  }

  public FileIncludeInfo(@NotNull String path) {
    this(path, -1);
  }

  private static String getFileName(String path) {
    int pos = path.lastIndexOf('/');
    return pos == -1 ? path : path.substring(pos + 1);
  }

}
