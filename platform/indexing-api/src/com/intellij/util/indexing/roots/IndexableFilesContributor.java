// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Predicate;

/**
 * A base interface to provide a files which should be indexed for a given project.
 *
 * @deprecated Not used by the platform anymore.
 * Files are added to the project (and set of indexed files) by creating
 * corresponding instance(s) of {@link com.intellij.platform.workspace.storage.WorkspaceEntity} and registering roots
 * in {@link com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndex}
 * by {@link com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndexContributor}
 */
@Deprecated(forRemoval = true)
@ApiStatus.OverrideOnly
public interface IndexableFilesContributor {

  /**
   * Returns ordered list of logical file sets (module files, SDK files, etc) to be indexed. Note:
   * <ul>
   * <li>The method is called in read-action with valid {@param project}.</li>
   * <li>{@link IndexableFilesIterator}-s will be indexed in provided order.</li>
   * <li>Files in {@link IndexableFilesIterator} should be not evaluated eagerly for performance reasons.</li>
   * </ul>
   */
  @NotNull
  List<IndexableFilesIterator> getIndexableFiles(@NotNull Project project);

  /**
   * Quickly should answer does file belongs to files contributor.
   * Used to filter out file events which is required to update indexes.
   */
  @NotNull
  Predicate<VirtualFile> getOwnFilePredicate(@NotNull Project project);
}
