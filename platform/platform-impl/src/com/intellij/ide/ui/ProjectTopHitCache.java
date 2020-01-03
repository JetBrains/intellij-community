// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class ProjectTopHitCache extends TopHitCache {
  public static TopHitCache getInstance(Project project) {
    return ServiceManager.getService(project, ProjectTopHitCache.class);
  }

  public ProjectTopHitCache(@NotNull Project project) {
    OptionsTopHitProvider.PROJECT_LEVEL_EP.addExtensionPointListener(
      new ExtensionPointListener<OptionsSearchTopHitProvider.ProjectLevelProvider>() {
        @Override
        public void extensionRemoved(@NotNull OptionsSearchTopHitProvider.ProjectLevelProvider extension,
                                     @NotNull PluginDescriptor pluginDescriptor) {
          map.remove(extension.getClass());
        }
      }, project);
  }
}
