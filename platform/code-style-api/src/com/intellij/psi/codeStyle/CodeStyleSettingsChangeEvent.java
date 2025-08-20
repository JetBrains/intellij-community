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
  private final @NotNull  Project     myProject;
  private final @Nullable VirtualFile myFile;
  private final @Nullable CodeStyleSettings mySettings;

  @ApiStatus.Internal
  public CodeStyleSettingsChangeEvent(@NotNull Project project, @Nullable VirtualFile file) {
    this(project, file, null);
  }

  @ApiStatus.Internal
  public CodeStyleSettingsChangeEvent(@NotNull Project project, @Nullable VirtualFile file, @Nullable CodeStyleSettings settings) {
    myFile = file;
    myProject = project;
    mySettings = settings;
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

  /**
   * Non-null value if the reason for the event is that async computation of code style settings just finished.
   * Non-null value also implies {@link #getVirtualFile()} is not null.
   * <p>
   * If non-null, the value should be used directly when handling the event instead of
   * {@link com.intellij.application.options.CodeStyle#getSettings(Project, VirtualFile)},
   * or similar file-specific calls.
   *
   * @return The current code style settings for {@link #getVirtualFile()}
   */
  @ApiStatus.Experimental
  public @Nullable CodeStyleSettings getSettings() {
    return mySettings;
  }
}
