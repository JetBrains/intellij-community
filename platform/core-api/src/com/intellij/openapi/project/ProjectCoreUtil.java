/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.project;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.InternalFileType;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author dmitrylomov
 */
public class ProjectCoreUtil {
  /** @deprecated use {@link Project#DIRECTORY_STORE_FOLDER} (to be removed in IDEA 17) */
  @SuppressWarnings("unused")
  public static final String DIRECTORY_BASED_PROJECT_DIR = Project.DIRECTORY_STORE_FOLDER;

  public static boolean isProjectOrWorkspaceFile(@NotNull VirtualFile file) {
    return isProjectOrWorkspaceFile(file, file.getFileType());
  }

  public static boolean isProjectOrWorkspaceFile(@NotNull VirtualFile file, @Nullable FileType fileType) {
    if (fileType instanceof InternalFileType) return true;
    VirtualFile parent = file.isDirectory() ? file: file.getParent();
    while (parent != null) {
      if (Comparing.equal(parent.getNameSequence(), Project.DIRECTORY_STORE_FOLDER, SystemInfoRt.isFileSystemCaseSensitive)) return true;
      parent = parent.getParent();
    }
    return false;
  }

  /**
   * @return the only open project if there is one, null if no or several projects are open
   */
  @Nullable
  public static Project theOnlyOpenProject() {
    return theProject;
  }
  public static volatile Project theProject;
}