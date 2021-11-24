// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.extension;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * Implement to suppress showing JSON widget for particular files where assistance is powered by a custom provider.
 */
public interface JsonWidgetSuppressor {
  ExtensionPointName<JsonWidgetSuppressor> EXTENSION_POINT_NAME = ExtensionPointName.create("com.intellij.json.jsonWidgetSuppressor");

  /**
   * Allows to check whether widget for the file should be suppressed or not.
   * This method is called on EDT.
   */
  default boolean isCandidateForSuppress(@NotNull VirtualFile file, @NotNull Project project) {
    return false;
  }

  /**
   * Allows to suppress JSON widget for particular files.
   * This method is called only if {@link #isCandidateForSuppress(VirtualFile, Project)}
   * return {@code true} for the given file in the given project.
   * <br>This method is called on a background thread under read action with progress indicator.
   * Implementors might want to call {@link com.intellij.openapi.progress.ProgressManager#checkCanceled()}
   * time to time to check whether widget suppression is still actual for the given file.
   * For instance progress indicator is canceled if another editor tab is selected.
   */
  boolean suppressSwitcherWidget(@NotNull VirtualFile file, @NotNull Project project);
}
