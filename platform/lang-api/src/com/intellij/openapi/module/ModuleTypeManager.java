// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.module;

import com.intellij.openapi.components.ServiceManager;
import org.jetbrains.annotations.Nullable;

public abstract class ModuleTypeManager {
  public static ModuleTypeManager getInstance() {
    return ServiceManager.getService(ModuleTypeManager.class);
  }

  public abstract void registerModuleType(ModuleType<?> type);

  public abstract ModuleType<?>[] getRegisteredTypes();

  public abstract ModuleType<?> findByID(@Nullable String moduleTypeID);

  public abstract void registerModuleType(ModuleType<?> type, boolean classpathProvider);

  public abstract boolean isClasspathProvider(ModuleType<?> moduleType);

  public abstract ModuleType<?> getDefaultModuleType();
}