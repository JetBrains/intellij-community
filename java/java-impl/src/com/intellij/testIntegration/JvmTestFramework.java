// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testIntegration;

import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.openapi.roots.ExternalLibraryDescriptor;

public interface JvmTestFramework extends TestFramework {
  ExternalLibraryDescriptor getFrameworkLibraryDescriptor();

  default boolean isMyConfigurationType(ConfigurationType type) {
    return false;
  }
}