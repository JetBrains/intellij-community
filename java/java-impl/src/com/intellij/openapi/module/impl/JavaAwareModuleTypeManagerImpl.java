// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.module.impl;

import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.StdModuleTypes;
import org.jetbrains.annotations.NonNls;

final class JavaAwareModuleTypeManagerImpl extends ModuleTypeManagerImpl{
  @NonNls private static final String JAVA_MODULE_ID_OLD = "JAVA";

  @Override
  public ModuleType<?> getDefaultModuleType() {
    return StdModuleTypes.JAVA;
  }

  @Override
  public ModuleType<?> findByID(final String moduleTypeID) {
    if (moduleTypeID != null) {
      if (JAVA_MODULE_ID_OLD.equals(moduleTypeID)) {
        return StdModuleTypes.JAVA; // for compatibility with the previous ID that Java modules had
      }
    }
    return super.findByID(moduleTypeID);
  }
}