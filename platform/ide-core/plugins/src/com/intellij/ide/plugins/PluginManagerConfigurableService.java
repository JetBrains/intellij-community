// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface PluginManagerConfigurableService {
  static PluginManagerConfigurableService getInstance() {
    return ApplicationManager.getApplication().getService(PluginManagerConfigurableService.class);
  }

  void showPluginConfigurableAndEnable(@Nullable Project project,
                                       String @NotNull ... plugins);
}
