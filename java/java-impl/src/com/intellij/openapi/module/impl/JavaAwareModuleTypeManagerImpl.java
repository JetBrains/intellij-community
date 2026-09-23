// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.module.impl;

import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.ModuleType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class JavaAwareModuleTypeManagerImpl extends ModuleTypeManagerImpl{
  private static final @NonNls String JAVA_MODULE_ID_OLD = "JAVA";

  private static final JavaModuleType JAVA_MODULE_TYPE = new JavaModuleType();

  @Override
  public @NotNull ModuleType<?> getDefaultModuleType() {
    return JAVA_MODULE_TYPE;
  }

  @Override
  public @NotNull ModuleType<?> findByID(@Nullable String moduleTypeId) {
    if (moduleTypeId != null) {
      if (JAVA_MODULE_ID_OLD.equals(moduleTypeId)) {
        return JAVA_MODULE_TYPE; // for compatibility with the previous ID that Java modules had
      }
    }
    return super.findByID(moduleTypeId);
  }
}