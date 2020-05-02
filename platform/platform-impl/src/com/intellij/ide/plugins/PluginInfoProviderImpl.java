// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.ide.plugins.marketplace.MarketplaceRequests;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;

public class PluginInfoProviderImpl implements PluginInfoProvider {
  @Override
  @Nullable
  public List<PluginId> loadCachedPlugins() throws IOException {
    List<String> pluginsIds = MarketplaceRequests.getInstance().getMarketplaceCachedPlugins();
    if (pluginsIds == null) return null;
    return ContainerUtil.map(pluginsIds, id -> PluginId.getId(id));
  }

  @Override
  public List<PluginId> loadPlugins(@Nullable ProgressIndicator indicator) throws IOException {
    return ContainerUtil.map(MarketplaceRequests.getInstance().getMarketplacePlugins(indicator), id -> PluginId.getId(id));
  }
}
