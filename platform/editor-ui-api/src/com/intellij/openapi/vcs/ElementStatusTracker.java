// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * A tracker that provides change status for {@link PsiElement}. The status might not be precise.
 * Also, tracker is not guaranteed to work if the file containing the element is not opened in the editor.
 * 
 * @see FileStatusManager
 */
public interface ElementStatusTracker {
  static ElementStatusTracker getInstance(@NotNull Project project) {
    return project.getService(ElementStatusTracker.class);
  }

  /**
   * @param element element to get the status for
   * @return status of the element: one of {@link FileStatus#ADDED}, {@link FileStatus#MODIFIED}, or {@link FileStatus#NOT_CHANGED}.
   * {@link FileStatus#NOT_CHANGED} status may be returned also if there's no VCS, or the element is not in the opened editor. 
   */
  @NotNull FileStatus getElementStatus(@NotNull PsiElement element);
}
