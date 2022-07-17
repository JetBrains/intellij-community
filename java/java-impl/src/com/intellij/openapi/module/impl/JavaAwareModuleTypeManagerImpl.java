// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.module.impl;

import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.StdModuleTypes;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class JavaAwareModuleTypeManagerImpl extends ModuleTypeManagerImpl{
  @NonNls private static final String JAVA_MODULE_ID_OLD = "JAVA";

  @Override
  public @NotNull ModuleType<?> getDefaultModuleType() {
    return StdModuleTypes.JAVA;
  }

  @Override
  public @NotNull ModuleType<?> findByID(@Nullable String moduleTypeId) {
    if (moduleTypeId != null) {
      if (JAVA_MODULE_ID_OLD.equals(moduleTypeId)) {
        return StdModuleTypes.JAVA; // for compatibility with the previous ID that Java modules had
      }
    }
    return super.findByID(moduleTypeId);
  }
}