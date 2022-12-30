// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.project.manage;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

@ApiStatus.Experimental
public interface SourceFolderManager {

  static @NotNull SourceFolderManager getInstance(@NotNull Project project) {
    return project.getService(SourceFolderManager.class);
  }

  void setSourceFolderPackagePrefix(@NotNull String url, @Nullable String packagePrefix);

  void setSourceFolderGenerated(@NotNull String url, boolean generated);

  void addSourceFolder(@NotNull Module module, @NotNull String url, @NotNull JpsModuleSourceRootType<?> type);

  void removeSourceFolders(@NotNull Module module);

  @ApiStatus.Internal
  void rescanAndUpdateSourceFolders();
}
