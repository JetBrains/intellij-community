// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public interface SilentChangeVetoer {
  ExtensionPointName<SilentChangeVetoer> EP_NAME = ExtensionPointName.create("com.intellij.silentChangeVetoer");

  /**
   * Query all {@link SilentChangeVetoer} extensions about the status of the {@code virtualFile}
   * @return {@link ThreeState#NO} or {@link ThreeState#YES} if at least one extension returned that result; {@link ThreeState#UNSURE} otherwise
   */
  static @NotNull ThreeState extensionsAllowToChangeFileSilently(@NotNull Project project, @NotNull VirtualFile virtualFile) {
    // might access indexes (to determine the relevant VCS) so it must run in BGT
    ApplicationManager.getApplication().assertIsNonDispatchThread();
    for (SilentChangeVetoer extension : EP_NAME.getExtensionList()) {
      ThreeState override = extension.canChangeFileSilently(project, virtualFile);
      if (override == ThreeState.NO || override == ThreeState.YES) {
        return override;
      }
    }
    return ThreeState.UNSURE;
  }

  @NotNull
  ThreeState canChangeFileSilently(@NotNull Project project, @NotNull VirtualFile virtualFile);
}