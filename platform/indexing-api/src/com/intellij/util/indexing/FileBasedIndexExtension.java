// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Processor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.Collections;

/**
 * Extension point to implement a file-based inverted index.
 * <p>
 * See <a href="https://plugins.jetbrains.com/docs/intellij/file-based-indexes.html">SDK Docs</a> for reference.
 * <p>
 *   <ul>
 *     <li>
 *       {@link FileContent} represents an "input"/"document" in this case. To evaluate input data, one can access its name, PSI, text, and binary content.
 *       One must not use the file's parent name or sibling content because, in this case, we can't provide any guarantees that the index will be updated properly.
 *     </li>
 *     <li>
 *       Every index will be updated when some files matched to {@link FileBasedIndexExtension#getInputFilter()} are changed.
 *       If the changed file count is relatively small, it will be done lazily on the first index access.
 *       Otherwise an {@link com.intellij.openapi.project.DumbModeTask} will be queued to {@link com.intellij.openapi.project.DumbService}
 *     </li>
 *     <li>
 *       An implementation may depend on the input file's content or not. See {@link FileBasedIndexExtension#dependsOnFileContent()} to specify it.
 *       It might be useful because content reading produces IO operations that can slow down the indexing.
 *     </li>
 *     <li>
 *       To access the index, use dedicated methods in {@link FileBasedIndex}.
 *     </li>
 *     <li>
 *       NOTE: <b>V</b>-class must have {@link Object#equals(Object)} and {@link Object#hashCode()} properly defined: value deserialized
 *       from serialized binary data should equal the original one.
 *     </li>
 *   </ul>
 * </p>
 *
 * @see SingleEntryFileBasedIndexExtension to create an instance of forward index if some file's data should be cached on indexing phase.
 */
@ApiStatus.OverrideOnly
public abstract class FileBasedIndexExtension<K, V> extends IndexExtension<K, V, FileContent> {
  public static final ExtensionPointName<FileBasedIndexExtension<?, ?>> EXTENSION_POINT_NAME =
    new ExtensionPointName<>("com.intellij.fileBasedIndex");

  // Use VFS-based implementation for FilenameIndex
  @ApiStatus.Internal
  public static final boolean USE_VFS_FOR_FILENAME_INDEX = Boolean.parseBoolean(System.getProperty("indexing.filename.over.vfs", "true"));

  private static final int DEFAULT_CACHE_SIZE = 1024;

  @Override
  public abstract @NotNull ID<K, V> getName();

  /**
   * Filter for files which are supposed to be indexed by the {@link IndexExtension#getIndexer()}.
   * <p>
   * Usually {@link DefaultFileTypeSpecificInputFilter} can be used here to index only files with given file-type.
   * Note that checking only file's extension is usually error-prone way and it is preferred to check {@link VirtualFile#getFileType()}:
   * for example user can enforce language file as plain text one.
   */
  public abstract @NotNull FileBasedIndex.InputFilter getInputFilter();

  public abstract boolean dependsOnFileContent();

  public boolean indexDirectories() {
    return false;
  }

  /**
   * @see FileBasedIndexExtension#DEFAULT_CACHE_SIZE
   */
  public int getCacheSize() {
    return DEFAULT_CACHE_SIZE;
  }

  /**
   * For most indices the method should return an empty collection.
   *
   * @return collection of file types to which file size limit will not be applied when indexing.
   * This allows indexing of files whose limit exceeds {@link com.intellij.openapi.vfs.limits.FileSizeLimit#getIntellisenseLimit(FileType)}}.
   * <p>
   * Use carefully, because indexing large files may influence index update speed dramatically.
   */
  public @NotNull @Unmodifiable Collection<FileType> getFileTypesWithSizeLimitNotApplicable() {
    return Collections.emptyList();
  }

  @ApiStatus.Internal
  public boolean keyIsUniqueForIndexedFile() {
    return false;
  }

  /**
   * If true then {@code <key hash> -> <virtual file id>} mapping will be saved in the persistent index structure.
   * It will then be used inside {@link FileBasedIndex#processAllKeys(ID, Processor, Project)},
   * accepting {@link IdFilter} as a coarse filter to exclude keys from unrelated virtual files from further processing.
   * Otherwise, {@link IdFilter} parameter of this method will be ignored.
   * <p>
   * This property might help optimize "Navigate | File/Symbol" and completion performance in the case of multiple indexed projects.
   *
   * @see IdFilter#buildProjectIdFilter(Project, boolean)
   */
  public boolean traceKeyHashToVirtualFileMapping() {
    return false;
  }

  public boolean hasSnapshotMapping() {
    return false;
  }

  /**
   * Whether this index needs the forward mapping to be shared along with inverted index.
   * <p>
   * If this method returns {@code false}, it is an error to call {@link FileBasedIndex#getFileData(ID, VirtualFile, Project)}
   * for this {@link #getName() index}.
   */
  @ApiStatus.Internal
  @ApiStatus.Experimental
  public boolean needsForwardIndexWhenSharing() {
    return true;
  }

  /**
   * Whether this index is safe to be shared with the Shared Indexes plugin.
   * <p>
   * This is a way to exclude wrongly implemented indexes from the shared indexes to avoid possible bugs.
   * Any index can be implemented in a shareable way, so this method will be removed at some point.
   */
  @ApiStatus.Experimental
  public boolean canBeShared() {
    return true;
  }

  @ApiStatus.Internal
  public boolean enableWal() {
    return false;
  }
}
