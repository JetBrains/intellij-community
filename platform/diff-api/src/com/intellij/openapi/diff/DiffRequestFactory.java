// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diff;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Allows to initiate 3-way merge operations for multiple versions of content of a particular virtual file.
 * @deprecated use {@link com.intellij.diff.DiffRequestFactory} instead
 */
@Deprecated
@ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
public abstract class DiffRequestFactory {
  public static DiffRequestFactory getInstance() {
    return ApplicationManager.getApplication().getService(DiffRequestFactory.class);
  }

  /**
   * Creates a request for a merge operation. To execute the request, obtain the diff tool instance by calling
   * {@link DiffManager#getDiffTool()} and then call {@link DiffTool#show(DiffRequest)}.
   *
   * @param leftText                 First of the changed versions of the content (to be displayed in the left pane).
   * @param rightText                Second of the changed versions of the content (to be displayed in the right pane).
   * @param originalContent          The version of the content before changes.
   * @param file                     The file which is being merged.
   * @param project                  The project in the context of which the operation is executed.
   * @param okButtonPresentation     Parameter ignored.
   * @param cancelButtonPresentation Parameter ignored.
   * @return The merge operation request.
   */
  public abstract MergeRequest createMergeRequest(@NotNull String leftText,
                                                  @NotNull String rightText,
                                                  @NotNull String originalContent,
                                                  @NotNull VirtualFile file,
                                                  @Nullable Project project,
                                                  @Nullable ActionButtonPresentation okButtonPresentation,
                                                  @Nullable ActionButtonPresentation cancelButtonPresentation);

  public abstract MergeRequest create3WayDiffRequest(@NotNull String leftText,
                                                     @NotNull String rightText,
                                                     @NotNull String originalContent,
                                                     @Nullable Project project,
                                                     @Nullable ActionButtonPresentation okButtonPresentation,
                                                     @Nullable ActionButtonPresentation cancelButtonPresentation);

  public abstract MergeRequest create3WayDiffRequest(@NotNull String leftText,
                                                     @NotNull String rightText,
                                                     @NotNull String originalContent,
                                                     @Nullable FileType type,
                                                     @Nullable Project project,
                                                     @Nullable ActionButtonPresentation okButtonPresentation,
                                                     @Nullable ActionButtonPresentation cancelButtonPresentation);
}
