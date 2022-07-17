// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.stubs.StubIndexKey;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

@ApiStatus.Internal
public interface FileBasedIndexInfrastructureExtension {
  ExtensionPointName<FileBasedIndexInfrastructureExtension> EP_NAME = ExtensionPointName.create("com.intellij.fileBasedIndexInfrastructureExtension");

  interface FileIndexingStatusProcessor {
    /**
     * Serves as an optimization when time-consuming {@link FileIndexingStatusProcessor#processUpToDateFile(IndexedFile, int, ID)}
     * should not be called because takes no effect.
     */
    boolean shouldProcessUpToDateFiles();

    /**
     * Processes up to date file for given content-dependent index while "scanning files to index" in progress.
     * @return true if the up-to-date file has been reviewed and it its indexing must be skipped,
     * false if the up-to-date file must be re-indexed because previously associated data is not valid anymore.
     */
    boolean processUpToDateFile(@NotNull IndexedFile file, int inputId, @NotNull ID<?, ?> indexId);

    /**
     * Tries to index file given content-dependent index "scanning files to index" in progress before its content will be loaded.
     *
     * @return true if file was indexed by an extension.
     */
    boolean tryIndexFileWithoutContent(@NotNull IndexedFile file, int inputId, @NotNull ID<?, ?> indexId);

    /**
     * Whether the given file has index provided by this extension.
     */
    @ApiStatus.Experimental
    boolean hasIndexForFile(@NotNull VirtualFile file, int inputId, @NotNull FileBasedIndexExtension<?, ?> extension);
  }

  @Nullable
  FileIndexingStatusProcessor createFileIndexingStatusProcessor(@NotNull Project project);


  /**
   * Allows the extension point to replace the original {@link UpdatableIndex} for
   * the given {@param indexExtension} with a combined index (base part from {@link FileBasedIndexImpl} and customizable one)
   * that that uses the internal state of the extension to supply indexes
   * @return wrapper or null.
   */
  @Nullable
  <K, V> UpdatableIndex<K, V, FileContent, ?> combineIndex(@NotNull FileBasedIndexExtension<K, V> indexExtension,
                                                           @NotNull UpdatableIndex<K, V, FileContent, ?> baseIndex) throws IOException;


  /**
   * Notifies the extension to handle that version of existing file based index version has been changed.
   *
   * Actually {@link FileBasedIndex} notifies even if index composite version (extension version + implementation version)
   * is changed {@link FileBasedIndexImpl#getIndexExtensionVersion(FileBasedIndexExtension)}.
   *
   * @param indexId that version is updated.
   */
  void onFileBasedIndexVersionChanged(@NotNull ID<?, ?> indexId);

  /**
   * Notifies the extension to handle that version of existing stub index has been changed.
   *
   * @param indexId that version is updated.
   */
  void onStubIndexVersionChanged(@NotNull StubIndexKey<?, ?> indexId);

  /**
   * Executed when IntelliJ is open it's indexes (IDE start or plugin load/unload).
   * All necessarily needed connections and resources should be open here.
   *
   * This method and {@link FileBasedIndexInfrastructureExtension#shutdown()} synchronize
   * lifecycle of an extension with {@link FileBasedIndexImpl}.
   **/
  @NotNull
  InitializationResult initialize(@Nullable("null if default") String indexLayoutId);

  /**
   * @return index persistent state root for given extension, namely a place where all cached data will be stored.
   * Every index extension persistent data should be stored in `{@link PathManager#getIndexRoot()}/getPersistentStateRoot()` dir.
   */
  @Nullable
  default String getPersistentStateRoot() {
    return null;
  }

  /**
   * Executed when IntelliJ is requested to clear indexes. Each extension should reset its caches.
   * For example, it may happen on index invalidation.
   */
  void resetPersistentState();

  /**
   * Executed when IntelliJ is requested to clear  a particular index. Only data corresponding to requested index should be deleted.
   */
  void resetPersistentState(@NotNull ID<?, ?> indexId);

  /**
   * Executed when IntelliJ is shutting down it's indexes (IDE shutdown or plugin load/unload). It is the best time
   * for the component to flush it's state to the disk and close all pending connections.
   *
   * This method and {@link FileBasedIndexInfrastructureExtension#initialize()} synchronize
   * lifecycle of an extension with {@link FileBasedIndexImpl}.
   */
  void shutdown();

  /**
   * When index infrastructure extension change it's version (for example data format has been changed)
   * all indexed data should be invalidate and full index rebuild will be requested
   */
  int getVersion();

  enum InitializationResult {
    SUCCESSFULLY, INDEX_REBUILD_REQUIRED
  }
}
