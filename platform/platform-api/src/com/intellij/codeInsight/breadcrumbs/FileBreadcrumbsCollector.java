// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.breadcrumbs;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.ProjectExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.breadcrumbs.Crumb;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Allows to replace the mechanism of gathering breadcrumbs for a file.
 */
public abstract class FileBreadcrumbsCollector {
  
  public static final ProjectExtensionPointName<FileBreadcrumbsCollector> EP_NAME =
    new ProjectExtensionPointName<>("com.intellij.fileBreadcrumbsCollector");

  /**
   * Checks if this collector handles the given file.
   */
  @RequiresBackgroundThread
  public abstract boolean handlesFile(@NotNull VirtualFile virtualFile);

  /**
   * Adds event listeners required to redraw the breadcrumbs when the contents of the file changes.
   *
   * @param file           the file to watch
   * @param editor         current editor
   * @param disposable     the disposable used to detach listeners when the file is closed.
   * @param changesHandler the callback to be called when any changes are detected.
   */
  public abstract void watchForChanges(@NotNull VirtualFile file,
                                       @NotNull Editor editor,
                                       @NotNull Disposable disposable,
                                       @NotNull Runnable changesHandler);

  public abstract @NotNull Iterable<? extends Crumb> computeCrumbs(@NotNull VirtualFile virtualFile,
                                                                   @NotNull Document document,
                                                                   int offset,
                                                                   @Nullable Boolean forcedShown);

  @ApiStatus.Internal
  public boolean requiresProvider() { return true; }

  public static FileBreadcrumbsCollector findBreadcrumbsCollector(Project project, VirtualFile file) {
    if (file != null) {
      for (FileBreadcrumbsCollector extension : EP_NAME.getExtensions(project)) {
        if (extension.handlesFile(file)) {
          return extension;
        }
      }
    }
    return ContainerUtil.getLastItem(EP_NAME.getPoint(project).getExtensionList());
  }
}
