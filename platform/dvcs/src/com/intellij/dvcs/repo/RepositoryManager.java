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
package com.intellij.dvcs.repo;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * RepositoryManager initializes and stores {@link Repository repositories} for Git or Hgroots defined in the project.
 *
 * @author Kirill Likhodedov
 */
public interface RepositoryManager<T extends Repository> {

  /**
   * Returns the {@link Repository} which tracks the Git or Hg repository located in the given directory,
   * or {@code null} if the given file is not a vcs root known to this {@link com.intellij.openapi.project.Project}.
   */
  @Nullable
  T getRepositoryForRoot(@Nullable VirtualFile root);

  /**
   * Returns the {@link Repository} which the given file belongs to, or {@code null} if the file is not under any Git or Hg repository.
   */
  @Nullable
  T getRepositoryForFile(@NotNull VirtualFile file);

  /**
   * Returns the {@link Repository} which the given file belongs to, or {@code null} if the file is not under any Git ot Hg repository.
   */
  @Nullable
  T getRepositoryForFile(@NotNull FilePath file);

  /**
   * @return all repositories tracked by the manager.
   */
  @NotNull
  List<T> getRepositories();

  boolean moreThanOneRoot();

  /**
   * Synchronously updates the specified information about repository under the given root.
   *
   * @param root root directory of the vcs repository.
   */
  void updateRepository(VirtualFile root);

  void updateAllRepositories();

  void waitUntilInitialized();
}
