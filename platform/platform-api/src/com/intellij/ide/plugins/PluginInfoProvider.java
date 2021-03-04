// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.concurrent.Future;

public interface PluginInfoProvider {
  /**
   * Reads cached plugin ids from a file. Returns {@code null} if cache file does not exist.
   */
  @Nullable Set<PluginId> loadCachedPlugins();

  /**
   * Loads list of plugins ids, compatible with a current build, from a main plugin repository.
   */
  @NotNull Future<Set<PluginId>> loadPlugins(@Nullable ProgressIndicator indicator);

  default @NotNull Future<Set<PluginId>> loadPlugins() {
    return loadPlugins(null);
  }

  static PluginInfoProvider getInstance() {
    return ApplicationManager.getApplication().getService(PluginInfoProvider.class);
  }
}
