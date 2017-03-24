/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Processor;
import com.intellij.util.indexing.ID;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

import static com.intellij.psi.search.FilenameIndex.getService;

/**
 * @author Dmitry Avdeev
 */
public class FileTypeIndex {

  /**
   * @deprecated Not to be used.
   */
  @Deprecated
  public static final ID<FileType, Void> NAME = ID.create("filetypes");

  @NotNull
  public static Collection<VirtualFile> getFiles(@NotNull FileType fileType, @NotNull GlobalSearchScope scope) {
    return getService().getFilesWithFileType(fileType, scope);
  }

  public static boolean containsFileOfType(@NotNull FileType type, @NotNull GlobalSearchScope scope) {
    return !getService().processFilesWithFileType(type, file -> false, scope);
  }

  public static boolean processFiles(@NotNull FileType fileType, @NotNull Processor<VirtualFile> processor, @NotNull GlobalSearchScope scope) {
    return getService().processFilesWithFileType(fileType, processor, scope);
  }
}
