// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Generated when the current code style settings change either for the entire project or for a specific file.
 */
public final class CodeStyleSettingsChangeEvent {
  private final @NotNull Project myProject;
  private final @Nullable VirtualFile myFile;

  @ApiStatus.Internal
  public CodeStyleSettingsChangeEvent(@NotNull Project project, @Nullable VirtualFile file) {
    myFile = file;
    myProject = project;
  }

  /**
   * @return The virtual file whose code style settings has changed, or null if the change is project-wide.
   */
  public @Nullable VirtualFile getVirtualFile() {
    return myFile;
  }

  /**
   * @return The project for which the even has been initiated.
   */
  public @NotNull Project getProject() {
    return myProject;
  }
}
