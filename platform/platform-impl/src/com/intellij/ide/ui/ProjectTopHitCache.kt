// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

@Service(Service.Level.PROJECT)
public final class ProjectTopHitCache extends TopHitCache {
  public static TopHitCache getInstance(@NotNull Project project) {
    return project.getService(ProjectTopHitCache.class);
  }

  public ProjectTopHitCache(@NotNull Project project) {
    OptionsTopHitProvider.PROJECT_LEVEL_EP.addExtensionPointListener(new ExtensionPointListener<>() {
      @Override
      public void extensionRemoved(@NotNull OptionsSearchTopHitProvider.ProjectLevelProvider extension,
                                   @NotNull PluginDescriptor pluginDescriptor) {
        invalidateCachedOptions(extension.getClass());
      }
    }, project);
  }
}
