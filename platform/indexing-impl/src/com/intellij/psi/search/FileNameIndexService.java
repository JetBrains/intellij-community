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
package com.intellij.psi.search;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Processor;
import com.intellij.util.indexing.IdFilter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

interface FileNameIndexService {
  @NotNull
  Collection<VirtualFile> getVirtualFilesByName(Project project, @NotNull String name, @NotNull GlobalSearchScope scope, @Nullable IdFilter idFilter);

  void processAllFileNames(@NotNull Processor<String> processor, @NotNull GlobalSearchScope scope, @Nullable IdFilter filter);

  @NotNull
  Collection<VirtualFile> getFilesWithFileType(@NotNull FileType type, @NotNull GlobalSearchScope scope);

  boolean processFilesWithFileType(@NotNull FileType type, @NotNull Processor<VirtualFile> processor, @NotNull GlobalSearchScope scope);
}
