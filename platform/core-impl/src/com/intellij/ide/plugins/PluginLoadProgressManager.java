// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.ide.StartupProgress;
import com.intellij.openapi.application.PathManager;
import org.jetbrains.annotations.NotNull;

import java.io.File;

final class PluginLoadProgressManager {
  private final StartupProgress progress;
  private final int pluginsCount;

  private int loadedCount;

  PluginLoadProgressManager(@NotNull StartupProgress progress, int urlCountFromClassPath) {
    this.progress = progress;
    this.pluginsCount = countPlugins(PathManager.getPluginsPath()) + countPlugins(PathManager.getPreInstalledPluginsPath()) + urlCountFromClassPath;
  }

  void showProgress(@NotNull IdeaPluginDescriptor descriptor) {
    loadedCount++;
    // show progress only for each 20 plugin to reduce influence of showProgress to start-up time
    if (loadedCount % 20 == 0) {
      progress.showProgress(descriptor.getName(), PluginManagerCore.PLUGINS_PROGRESS_PART * ((float)loadedCount / pluginsCount));
    }
  }

  private static int countPlugins(@NotNull String pluginsPath) {
    String[] list = new File(pluginsPath).list();
    return list == null ? 0 : list.length;
  }
}
