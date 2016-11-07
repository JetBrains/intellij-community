/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.util.gist;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Calculates some data based on {@link VirtualFile} content, stores that data persistently and updates it when the content is changed. The data is calculated lazily, when needed, and can be different for different projects.<p/>
 *
 * Obtained using {@link GistManager#newVirtualFileGist}.<p/>
 *
 * Tracks VFS content only. Unsaved/uncommitted documents have no effect on the {@link #getFileData} results.
 * Neither do any disk file changes, until VFS refresh has detected them.
 *
 * @see PsiFileGist
 * @since 171.*
 * @author peter
 */
public interface VirtualFileGist<Data> {

  /**
   * Calculate or get the cached data by the current virtual file content in the given project.
   */
  @Nullable
  Data getFileData(@NotNull Project project, @NotNull VirtualFile file);

  /**
   * Used by {@link VirtualFileGist} to calculate the data when it's needed and to recalculate it after file changes.
   */
  @FunctionalInterface
  interface GistCalculator<Data> {

    @Nullable
    Data calcData(@NotNull Project project, @NotNull VirtualFile file);
  }
}
