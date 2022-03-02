// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.module;

import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class ModuleTypeManager {
  public static ModuleTypeManager getInstance() {
    return ApplicationManager.getApplication().getService(ModuleTypeManager.class);
  }

  /**
   * This method is intended for internal use only. Use {@code com.intellij.moduleType} extension point to register custom module type in
   * a plugin.
   */
  @ApiStatus.Internal
  public abstract void registerModuleType(@NotNull ModuleType<?> type);

  @ApiStatus.Internal
  public abstract void unregisterModuleType(@NotNull ModuleType<?> type);

  public abstract @NotNull List<ModuleType<?>> getRegisteredTypes();

  public abstract @NotNull ModuleType<?> findByID(@Nullable String moduleTypeID);

  public abstract void registerModuleType(@NotNull ModuleType<?> type, boolean classpathProvider);

  public abstract boolean isClasspathProvider(@NotNull ModuleType<?> moduleType);

  public abstract @NotNull ModuleType<?> getDefaultModuleType();
}
