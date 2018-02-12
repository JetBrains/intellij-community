/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.execution;

import com.intellij.execution.configurations.ConfigurationWithAlternativeJre;
import org.jetbrains.annotations.Nullable;

public interface CommonJavaRunConfigurationParameters extends CommonProgramRunConfigurationParameters, ConfigurationWithAlternativeJre {
  void setVMParameters(@Nullable String value);

  String getVMParameters();

  boolean isAlternativeJrePathEnabled();

  void setAlternativeJrePathEnabled(boolean enabled);

  @Nullable
  String getAlternativeJrePath();

  void setAlternativeJrePath(@Nullable String path);

  @Nullable
  String getRunClass();

  @Nullable
  String getPackage();
}
