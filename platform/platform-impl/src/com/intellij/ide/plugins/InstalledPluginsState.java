/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.plugins;

import com.intellij.idea.IdeaApplication;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.updateSettings.impl.PluginDownloader;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.SmartHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * A service to hold a state of plugin changes in a current session (i.e. before the changes are applied on restart).
 */
public class InstalledPluginsState {
  @Nullable
  public static InstalledPluginsState getInstanceIfLoaded() {
    return IdeaApplication.isLoaded() ? getInstance() : null;
  }

  public static InstalledPluginsState getInstance() {
    return ServiceManager.getService(InstalledPluginsState.class);
  }

  private final Object myLock = new Object();
  private final Map<PluginId, IdeaPluginDescriptor> myInstalledPlugins = ContainerUtil.newIdentityHashMap();
  private final Map<PluginId, IdeaPluginDescriptor> myUpdatedPlugins = ContainerUtil.newIdentityHashMap();
  private final Set<String> myOutdatedPlugins = new SmartHashSet<>();

  @NotNull
  public Collection<IdeaPluginDescriptor> getInstalledPlugins() {
    synchronized (myLock) {
      return Collections.unmodifiableCollection(myInstalledPlugins.values());
    }
  }

  public boolean hasNewerVersion(@NotNull PluginId id) {
    synchronized (myLock) {
      return !wasUpdated(id) && myOutdatedPlugins.contains(id.getIdString());
    }
  }

  public boolean wasInstalled(@NotNull PluginId id) {
    synchronized (myLock) {
      return myInstalledPlugins.containsKey(id);
    }
  }

  public boolean wasUpdated(@NotNull PluginId id) {
    synchronized (myLock) {
      return myUpdatedPlugins.containsKey(id);
    }
  }

  /**
   * Should be called whenever a list of plugins is loaded from a repository to check if there is an updated version.
   */
  public void onDescriptorDownload(@NotNull IdeaPluginDescriptor descriptor) {
    PluginId id = descriptor.getPluginId();
    IdeaPluginDescriptor existing = PluginManager.getPlugin(id);
    if (existing == null || (existing.isBundled() && !existing.allowBundledUpdate()) || wasUpdated(id)) {
      return;
    }

    boolean supersedes = PluginManagerCore.isCompatible(descriptor) &&
                         PluginDownloader.compareVersionsSkipBrokenAndIncompatible(existing, descriptor.getVersion()) > 0;

    String idString = id.getIdString();

    synchronized (myLock) {
      if (supersedes) {
        myOutdatedPlugins.add(idString);
      }
      else {
        myOutdatedPlugins.remove(idString);
      }
    }
  }

  /**
   * Should be called whenever a new plugin is installed or an existing one is updated.
   */
  public void onPluginInstall(@NotNull IdeaPluginDescriptor descriptor) {
    PluginId id = descriptor.getPluginId();
    boolean existing = PluginManager.isPluginInstalled(id);

    synchronized (myLock) {
      myOutdatedPlugins.remove(id.getIdString());
      if (existing) {
        myUpdatedPlugins.put(id, descriptor);
      }
      else {
        myInstalledPlugins.put(id, descriptor);
      }
    }
  }
}
