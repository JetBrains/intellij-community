// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.configurations;

import com.intellij.openapi.extensions.ProjectExtensionPointName;
import com.intellij.openapi.options.UnnamedConfigurable;
import org.jetbrains.annotations.NotNull;

public interface RunConfigurationsSettings {
  ProjectExtensionPointName<RunConfigurationsSettings> EXTENSION_POINT = new ProjectExtensionPointName<>("com.intellij.runConfigurationsSettings");

  @NotNull
  UnnamedConfigurable createConfigurable();
}