/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.execution;

import com.intellij.execution.configurations.ConfigurationWithAlternativeJre;
import com.intellij.execution.configurations.ModuleBasedConfigurationOptions;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public interface CommonJavaRunConfigurationParameters extends CommonProgramRunConfigurationParameters, ConfigurationWithAlternativeJre {
  void setVMParameters(@Nullable String value);

  String getVMParameters();

  @Override
  boolean isAlternativeJrePathEnabled();

  void setAlternativeJrePathEnabled(boolean enabled);

  @Override
  @Nullable
  String getAlternativeJrePath();

  void setAlternativeJrePath(@Nullable String path);

  @Nullable
  String getRunClass();

  @Nullable
  String getPackage();

  default List<ModuleBasedConfigurationOptions.ClasspathModification> getClasspathModifications() {
    return Collections.emptyList();
  }

  default void setClasspathModifications(List<ModuleBasedConfigurationOptions.ClasspathModification> modifications) {
  }
}
