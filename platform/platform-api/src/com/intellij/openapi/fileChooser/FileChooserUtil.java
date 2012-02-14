/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.fileChooser;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Temporary class, do not use.
 * Will be removed after migration from file choosing to path choosing (approx. in IDEA 13).
 *
 * @author Roman Shevchenko
 */
public final class FileChooserUtil {
  private static final Key<String> PATH_KEY = Key.create("FileChooser.Path");

  @NotNull
  public static String getSelectionPath(@NotNull final VirtualFile file) {
    final String path = file.getUserData(PATH_KEY);
    return path != null ? path : file.getPath();
  }

  public static void setSelectionPath(@NotNull final VirtualFile file, @Nullable final String path) {
    file.putUserData(PATH_KEY, path);
  }

  @NotNull
  public static String[] getPaths(@NotNull final VirtualFile[] files) {
    if (files.length == 0) return ArrayUtil.EMPTY_STRING_ARRAY;

    final String[] paths = new String[files.length];
    for (int i = 0; i < files.length; i++) {
      paths[i] = getSelectionPath(files[i]);
    }
    return paths;
  }
}
