// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.search;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.ID;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.ApiStatus.Experimental;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.ApiStatus.OverrideOnly;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

/**
 * @author Dmitry Avdeev
 */
public final class FileTypeIndex {

  private FileTypeIndex() {
  }

  /**
   * @deprecated please don't use this index directly.
   *
   * Use {@link #getFiles(FileType, GlobalSearchScope)},
   * {@link #containsFileOfType(FileType, GlobalSearchScope)} or
   * {@link #processFiles(FileType, Processor, GlobalSearchScope)} instead
   */
  @Deprecated
  @Internal
  public static final ID<FileType, Void> NAME = ID.create("filetypes");

  public static @Nullable FileType getIndexedFileType(@NotNull VirtualFile file, @NotNull Project project) {
    Map<FileType, Void> data = FileBasedIndex.getInstance().getFileData(NAME, file, project);
    return ContainerUtil.getFirstItem(data.keySet());
  }

  public static @NotNull @Unmodifiable Collection<VirtualFile> getFiles(@NotNull FileType fileType, @NotNull GlobalSearchScope scope) {
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

  @Experimental
  @Topic.AppLevel
  public static final Topic<IndexChangeListener> INDEX_CHANGE_TOPIC =
    new Topic<>(IndexChangeListener.class, Topic.BroadcastDirection.TO_DIRECT_CHILDREN);

  @Experimental
  @OverrideOnly
  public interface IndexChangeListener {
    /**
     * This event means that the set of files corresponding to the {@code fileType} has changed
     * (i.e. gets fired for both additions and removals).
     */
    void onChangedForFileType(@NotNull FileType fileType);
  }
}
