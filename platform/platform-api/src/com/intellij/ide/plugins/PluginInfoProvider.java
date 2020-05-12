// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;

public interface PluginInfoProvider {

  /**
   * Reads cached plugin ids from a file. Returns {@code null} if cache file does not exist.
   */
  List<PluginId> loadCachedPlugins() throws IOException;

  /**
   * Loads list of plugins ids, compatible with a current build, from a main plugin repository.
   */
  List<PluginId> loadPlugins(@Nullable ProgressIndicator indicator) throws IOException;
}
