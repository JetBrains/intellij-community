// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

@ApiStatus.Internal
public interface FileBasedIndexInfrastructureExtension {
  ExtensionPointName<FileBasedIndexInfrastructureExtension> EP_NAME =  ExtensionPointName.create("com.intellij.fileBasedIndexInfrastructureExtension");

  /**
   * This notification is send from an IDE to let the extension point implementation
   * update it's internal state in order to supply indexes for the given {@param entries}.
   *
   * Called every time when project structure is updated.
   */
  void processProjectEntries(@NotNull Project project,
                             @NotNull Set<OrderEntry> entries,
                             @NotNull ProgressIndicator indicator);


  interface FileIndexingStatusProcessor {
    /**
     * Processes up to date file while "scanning files to index" in progress.
     */
    void processUpToDateFile(@NotNull VirtualFile file, int inputId, @NotNull ID<?, ?> indexId);
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
  <K, V> UpdatableIndex<K, V, FileContent> combineIndex(@NotNull FileBasedIndexExtension<K, V> indexExtension,
                                                        @NotNull UpdatableIndex<K, V, FileContent> baseIndex);


  /**
   * Executed when IntelliJ is shutting down it's indexes (IDE shutdown or plugin load/unload). It is the best time
   * for the component to flush it's state to the disk and close all pending connections.
   * TODO: Meanwhile, the implementation should be able to re-open necessary connections if
   * TODO: other methods of the interface are executed after the call to {@link #performShutdown()}
   */
  void performShutdown();

}
