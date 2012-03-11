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

import com.google.common.collect.Lists;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * Temporary class, do not use.
 * May be removed after migration from file choosing to path choosing (approx. in IDEA 13).
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
  public static String[] filesToPaths(@Nullable final VirtualFile[] files) {
    if (files == null || files.length == 0) return ArrayUtil.EMPTY_STRING_ARRAY;

    final String[] paths = new String[files.length];
    for (int i = 0; i < files.length; i++) {
      paths[i] = getSelectionPath(files[i]);
    }
    return paths;
  }

  @Nullable
  public static VirtualFile pathToFile(@Nullable final String path, final boolean refresh) {
    if (path == null) return null;
    final String vfsPath = FileUtil.toSystemIndependentName(path);
    final LocalFileSystem fs = LocalFileSystem.getInstance();
    final VirtualFile file = refresh ? fs.refreshAndFindFileByPath(vfsPath) : fs.findFileByPath(vfsPath);
    return file != null && file.isValid() ? file : null;
  }

  @NotNull
  public static List<VirtualFile> pathsToFiles(@Nullable final List<String> paths, final boolean refresh) {
    if (paths == null || paths.size() == 0) return Collections.emptyList();

    final LocalFileSystem fs = LocalFileSystem.getInstance();
    final List<VirtualFile> files = Lists.newArrayListWithExpectedSize(paths.size());
    for (String path : paths) {
      final String vfsPath = FileUtil.toSystemIndependentName(path);
      final VirtualFile file = refresh ? fs.refreshAndFindFileByPath(vfsPath) : fs.findFileByPath(vfsPath);
      if (file != null && file.isValid()) {
        files.add(file);
      }
    }
    return files;
  }

  @Nullable
  public static String getPathToSelect(@NotNull FileChooserDescriptor descriptor,
                                       @Nullable Project project,
                                       @Nullable String toSelect,
                                       @Nullable String lastPath) {
    if (toSelect == null && lastPath == null) {
      if (project != null) {
        final VirtualFile baseDir = project.getBaseDir();
        if (baseDir != null) {
          return baseDir.getPath();
        }
      }
    }
    else if (toSelect != null && lastPath != null) {
      if (Boolean.TRUE.equals(descriptor.getUserData(PathChooserDialog.PREFER_LAST_OVER_EXPLICIT))) {
        return toSelect;
      }
      else {
        return lastPath;
      }
    }
    else if (toSelect == null) {
      return lastPath;
    }
    else {
      return toSelect;
    }

    return null;
  }
}
