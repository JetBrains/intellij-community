// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;

public class PluginInfoProviderImpl implements PluginInfoProvider {
  @Override
  public List<IdeaPluginDescriptor> loadCachedPlugins() throws IOException {
    return RepositoryHelper.loadCachedPlugins();
  }

  @Override
  public List<IdeaPluginDescriptor> loadPlugins(@Nullable ProgressIndicator indicator) throws IOException {
    return RepositoryHelper.loadPlugins(indicator);
  }
}
