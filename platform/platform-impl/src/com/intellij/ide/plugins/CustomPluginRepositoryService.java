// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.updateSettings.impl.UpdateChecker;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public final class CustomPluginRepositoryService {
  public static CustomPluginRepositoryService getInstance() {
    return ServiceManager.getService(CustomPluginRepositoryService.class);
  }

  private Collection<IdeaPluginDescriptor> myCustomRepositoryPluginsList;
  private Map<String, List<IdeaPluginDescriptor>> myCustomRepositoryPluginsMap;
  private final Object myRepositoriesLock = new Object();

  private static final Logger LOG = Logger.getInstance(CustomPluginRepositoryService.class);

  @NotNull
  public Map<String, List<IdeaPluginDescriptor>> getCustomRepositoryPluginMap() {
    synchronized (myRepositoriesLock) {
      if (myCustomRepositoryPluginsMap != null) {
        return myCustomRepositoryPluginsMap;
      }
    }
    Map<PluginId, IdeaPluginDescriptor> latestCustomPluginsAsMap = new HashMap<>();
    Map<String, List<IdeaPluginDescriptor>> customRepositoryPluginsMap = new HashMap<>();
    for (String host : RepositoryHelper.getPluginHosts()) {
      try {
        if (host != null) {
          List<IdeaPluginDescriptor> descriptors = RepositoryHelper.loadPlugins(host, null);
          for (IdeaPluginDescriptor descriptor : descriptors) {
            PluginId pluginId = descriptor.getPluginId();
            IdeaPluginDescriptor savedDescriptor = latestCustomPluginsAsMap.get(pluginId);
            if (savedDescriptor == null) {
              latestCustomPluginsAsMap.put(pluginId, descriptor);
            } else {
              if (StringUtil.compareVersionNumbers(descriptor.getVersion(), savedDescriptor.getVersion()) > 0) {
                latestCustomPluginsAsMap.put(pluginId, descriptor);
              }
            }
          }
          customRepositoryPluginsMap.put(host, descriptors);
        }
      }
      catch (IOException e) {
        LOG.info(host, e);
      }
    }

    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      UpdateChecker.updateDescriptorsForInstalledPlugins(InstalledPluginsState.getInstance());
    });

    synchronized (myRepositoriesLock) {
      if (myCustomRepositoryPluginsMap == null) {
        myCustomRepositoryPluginsMap = customRepositoryPluginsMap;
        myCustomRepositoryPluginsList = latestCustomPluginsAsMap.values();
      }
      return myCustomRepositoryPluginsMap;
    }
  }

  public Collection<IdeaPluginDescriptor> getCustomRepositoryPlugins() {
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
