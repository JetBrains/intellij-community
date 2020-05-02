// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.module;

import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class ModuleTypeManager {
  public static ModuleTypeManager getInstance() {
    return ApplicationManager.getApplication().getService(ModuleTypeManager.class);
  }

  /**
   * This method is intended for internal use only. Use {@code com.intellij.moduleType} extension point to register custom module type in
   * a plugin.
   */
  @ApiStatus.Internal
  public abstract void registerModuleType(ModuleType<?> type);

  @ApiStatus.Internal
  public abstract void unregisterModuleType(ModuleType<?> type);

  public abstract ModuleType<?>[] getRegisteredTypes();

  public abstract ModuleType<?> findByID(@Nullable String moduleTypeID);

  public abstract void registerModuleType(ModuleType<?> type, boolean classpathProvider);

  public abstract boolean isClasspathProvider(@NotNull ModuleType<?> moduleType);

  public abstract ModuleType<?> getDefaultModuleType();
}