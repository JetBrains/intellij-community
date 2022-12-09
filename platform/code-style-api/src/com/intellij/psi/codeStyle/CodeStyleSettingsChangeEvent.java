// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.codeStyle;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Generated when the current code style settings change either for the entire project or for a specific file.
 */
public class CodeStyleSettingsChangeEvent {
  private @NotNull final  Project     myProject;
  private @Nullable final VirtualFile myFile;

  @ApiStatus.Internal
  public CodeStyleSettingsChangeEvent(@NotNull Project project, @Nullable VirtualFile file) {
    myFile = file;
    myProject = project;
  }

  /**
   * @return The virtual file whose code style settings has changed, or null if the change is project-wide.
   */
  @Nullable
  public final VirtualFile getVirtualFile() {
    return myFile;
  }

  /**
   * @return The project for which the even has been initiated.
   */
  public @NotNull Project getProject() {
    return myProject;
  }
}
