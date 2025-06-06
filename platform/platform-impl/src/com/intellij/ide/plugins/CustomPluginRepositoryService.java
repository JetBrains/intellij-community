// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.ide.plugins.auth.PluginRepositoryAuthListener;
import com.intellij.ide.plugins.newui.PluginUiModel;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.util.text.VersionComparatorUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public final class CustomPluginRepositoryService implements PluginRepositoryAuthListener {
  public static CustomPluginRepositoryService getInstance() {
    return ApplicationManager.getApplication().getService(CustomPluginRepositoryService.class);
  }

  public CustomPluginRepositoryService() {
    ApplicationManager
      .getApplication()
      .getMessageBus()
      .connect()
      .subscribe(PLUGIN_REPO_AUTH_CHANGED_TOPIC, this);
  }

  private Collection<PluginUiModel> myCustomRepositoryPluginsList;
  private Map<String, List<PluginUiModel>> myCustomRepositoryPluginsMap;
  private final Object myRepositoriesLock = new Object();

  private static final Logger LOG = Logger.getInstance(CustomPluginRepositoryService.class);

  public @NotNull Map<String, List<PluginUiModel>> getCustomRepositoryPluginMap() {
    synchronized (myRepositoriesLock) {
      if (myCustomRepositoryPluginsMap != null) {
        return myCustomRepositoryPluginsMap;
      }
    }

    Map<PluginId, PluginUiModel> latestCustomPluginsAsMap = new HashMap<>();
    Map<String, List<PluginUiModel>> customRepositoryPluginsMap = new HashMap<>();
    for (String host : RepositoryHelper.getCustomPluginRepositoryHosts()) {
      try {
        List<PluginUiModel> descriptors = RepositoryHelper.loadPluginModels(host, null, null);
        for (PluginUiModel descriptor : descriptors) {
          PluginId pluginId = descriptor.getPluginId();
          PluginUiModel savedDescriptor = latestCustomPluginsAsMap.get(pluginId);
          if (savedDescriptor == null || VersionComparatorUtil.compare(descriptor.getVersion(), savedDescriptor.getVersion()) > 0) {
            latestCustomPluginsAsMap.put(pluginId, descriptor);
          }
        }
        customRepositoryPluginsMap.put(host, descriptors);
      }
      catch (ProcessCanceledException e) {
        throw e;
      }
      catch (Exception e) {
        LOG.info("Failed to load plugins from " + host, e);
      }
    }

    synchronized (myRepositoriesLock) {
      if (myCustomRepositoryPluginsMap == null) {
        myCustomRepositoryPluginsMap = customRepositoryPluginsMap;
        myCustomRepositoryPluginsList = latestCustomPluginsAsMap.values();
      }
      return myCustomRepositoryPluginsMap;
    }
  }

  @Override
  public void authenticationChanged() {
    clearCache();
  }

  public @NotNull Collection<PluginUiModel> getCustomRepositoryPlugins() {
    synchronized (myRepositoriesLock) {
      if (myCustomRepositoryPluginsList != null) {
        return myCustomRepositoryPluginsList;
      }
    }
    getCustomRepositoryPluginMap();
    return myCustomRepositoryPluginsList;
  }

  public void clearCache() {
    synchronized (myRepositoriesLock) {
      myCustomRepositoryPluginsList = null;
      myCustomRepositoryPluginsMap = null;
    }
  }
}
