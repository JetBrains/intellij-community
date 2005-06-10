/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.module;

import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.extensions.AreaInstance;
import com.intellij.pom.PomModule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface Module extends ComponentManager, AreaInstance {
  Module[] EMPTY_ARRAY = new Module[0];

  VirtualFile getModuleFile();

  @NotNull String getModuleFilePath();

  @NotNull ModuleType getModuleType();

  @NotNull Project getProject();

  @NotNull String getName();

  boolean isDisposed();

  boolean isSavePathsRelative();

  void setSavePathsRelative(boolean b);

  void setOption(@NotNull String optionName, @NotNull String optionValue);

  @Nullable String getOptionValue(@NotNull String optionName);

  @NotNull PomModule getPom();
}
