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
package com.intellij.compiler.backwardRefs;

import com.intellij.openapi.compiler.options.ExcludeEntryDescription;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class ExcludedFromCompileFilesUtil {
  static GlobalSearchScope getExcludedFilesScope(@NotNull ExcludeEntryDescription[] descriptions,
                                                 @NotNull Set<FileType> fileTypes,
                                                 @NotNull Project project,
                                                 @NotNull ProjectFileIndex fileIndex) {
    ManagingFS fs = ManagingFS.getInstance();
    final Collection<VirtualFile> excludedFiles = Stream.of(descriptions)
      .flatMap(description -> {
        final VirtualFile file = description.getVirtualFile();
        if (file == null) return Stream.empty();
        if (description.isFile()) {
          return Stream.of(file);
        }
        else if (description.isIncludeSubdirectories()) {
          final Stream.Builder<VirtualFile> builder = Stream.builder();
          VfsUtilCore.iterateChildrenRecursively(file, f -> !f.isDirectory() || fs.areChildrenLoaded(f), f -> {
            builder.accept(f);
            return true;
          });
          return builder.build();
        }
        else {
          return fs.areChildrenLoaded(file) ? Stream.of(file.getChildren()) : Stream.empty();
        }
      })
      .filter(f -> !f.isDirectory() && fileTypes.contains(f.getFileType()) && fileIndex.isInSourceContent(f))
      .collect(Collectors.toList());

    return GlobalSearchScope.filesWithoutLibrariesScope(project, excludedFiles);
  }
}
