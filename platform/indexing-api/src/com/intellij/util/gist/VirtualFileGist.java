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
import com.intellij.util.indexing.FileBasedIndexExtension;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Calculates some data based on {@link VirtualFile} content, persists it between IDE restarts,
 * and updates it when the content is changed. The data is calculated lazily, when needed, and can be different for different projects.<p/>
 *
 * Obtained using {@link GistManager#newVirtualFileGist}.<p/>
 *
 * Tracks VFS content only. Unsaved/uncommitted documents have no effect on the {@link #getFileData} results.
 * Neither do any disk file changes, until VFS refresh has detected them. To work with PSI content, use
 * {@link PsiFileGist}.<p/>
 *
 * Please note that every call to {@link #getFileData} means disk access. Clients that access gists frequently
 * should take care of proper caching themselves. The data is calculated on demand when first requested, so if
 * you need this data for a lot of files at once, this can take some amount of time on the first query. If that's
 * unacceptable from the UX perspective, consider using {@link FileBasedIndexExtension} instead.<p/>
 *
 * The differences to file-based index:
 * <ul>
 *   <li>Gists have simpler lifecycle and API, but don't provide a possibility for queries across multiple files.</li>
 *   <li>Gists are project-dependent.</li>
 *   <li>Gists are calculated on request for specific files, index processes all files in advance. Thus gists can be
 *   used to speed up the indexing phase and move the logic into later stages, when it's beneficial.</li>
 * </ul>
 */
@ApiStatus.NonExtendable
public interface VirtualFileGist<Data> {

  /**
   * Calculate or get the cached data by the current virtual file content in the given project (or null, if the data is project-independent).
   */
  @Nullable Data getFileData(@Nullable Project project, @NotNull VirtualFile file);

  /**
   * Get the cached data by the virtual file content in the given project (or null, if the data is project-independent).
   */
  @Nullable Supplier<Data> getUpToDateOrNull(@Nullable Project project, @NotNull VirtualFile file);

  /**
   * Used by {@link VirtualFileGist} to calculate the data when it's needed and to recalculate it after file changes.
   */
  @FunctionalInterface
  interface GistCalculator<Data> {

    @Nullable
    Data calcData(Project project, @NotNull VirtualFile file);
  }
}
