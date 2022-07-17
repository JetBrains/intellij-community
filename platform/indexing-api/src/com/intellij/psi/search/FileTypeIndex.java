// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.search;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.ID;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

/**
 * @author Dmitry Avdeev
 */
public final class FileTypeIndex {
  /**
   * @deprecated please don't use this index directly.
   *
   * Use {@link #getFiles(FileType, GlobalSearchScope)},
   * {@link #containsFileOfType(FileType, GlobalSearchScope)} or
   * {@link #processFiles(FileType, Processor, GlobalSearchScope)} instead
   */
  @Deprecated
  @ApiStatus.Internal
  public static final ID<FileType, Void> NAME = ID.create("filetypes");

  @Nullable
  public static FileType getIndexedFileType(@NotNull VirtualFile file, @NotNull Project project) {
    Map<FileType, Void> data = FileBasedIndex.getInstance().getFileData(NAME, file, project);
    return ContainerUtil.getFirstItem(data.keySet());
  }

  @NotNull
  public static Collection<VirtualFile> getFiles(@NotNull FileType fileType, @NotNull GlobalSearchScope scope) {
    return FileBasedIndex.getInstance().getContainingFiles(NAME, fileType, scope);
  }

  public static boolean containsFileOfType(@NotNull FileType type, @NotNull GlobalSearchScope scope) {
    return !processFiles(type, CommonProcessors.alwaysFalse(), scope);
  }

  public static boolean processFiles(@NotNull FileType fileType, @NotNull Processor<? super VirtualFile> processor, @NotNull GlobalSearchScope scope) {
    @NotNull Iterator<VirtualFile> files = FileBasedIndex.getInstance().getContainingFilesIterator(NAME, fileType, scope);
    while (files.hasNext()) {
      VirtualFile file = files.next();
      if (!processor.process(file)) {
        return false;
      }
    }
    return true;
  }
}
