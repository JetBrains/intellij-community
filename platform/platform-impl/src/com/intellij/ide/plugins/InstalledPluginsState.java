// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.diagnostic.LoadingState;
import com.intellij.openapi.components.Service;
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
@Service
public final class InstalledPluginsState {
  @Nullable
  public static InstalledPluginsState getInstanceIfLoaded() {
    return LoadingState.COMPONENTS_LOADED.isOccurred() ? getInstance() : null;
  }

  public static InstalledPluginsState getInstance() {
    return ServiceManager.getService(InstalledPluginsState.class);
  }

  private final Object myLock = new Object();
  private final Map<PluginId, IdeaPluginDescriptor> myInstalledPlugins = ContainerUtil.newIdentityHashMap();
  private final Map<PluginId, IdeaPluginDescriptor> myInstalledWithoutRestartPlugins = ContainerUtil.newIdentityHashMap();
  private final Map<PluginId, IdeaPluginDescriptor> myUpdatedPlugins = ContainerUtil.newIdentityHashMap();
  private final Map<PluginId, IdeaPluginDescriptor> myUninstalledWithoutRestartPlugins = ContainerUtil.newIdentityHashMap();
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

  public boolean wasInstalledWithoutRestart(@NotNull PluginId id) {
    synchronized (myLock) {
      return myInstalledWithoutRestartPlugins.containsKey(id);
    }
  }

  public boolean wasUninstalledWithoutRestart(@NotNull PluginId id) {
    synchronized (myLock) {
      return myUninstalledWithoutRestartPlugins.containsKey(id);
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
    IdeaPluginDescriptor existing = PluginManagerCore.getPlugin(id);
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
  public void onPluginInstall(@NotNull IdeaPluginDescriptor descriptor, boolean isUpdate, boolean restartNeeded) {
    PluginId id = descriptor.getPluginId();
    synchronized (myLock) {
      myOutdatedPlugins.remove(id.getIdString());
      if (isUpdate) {
        myUpdatedPlugins.put(id, descriptor);
      }
      else if (restartNeeded) {
        myInstalledPlugins.put(id, descriptor);
      }
      else {
        myInstalledWithoutRestartPlugins.put(id, descriptor);
      }
    }
  }


  public void onPluginUninstall(@NotNull IdeaPluginDescriptor descriptor, boolean restartNeeded) {
    PluginId id = descriptor.getPluginId();
    synchronized (myLock) {
      if (!restartNeeded) {
        myUninstalledWithoutRestartPlugins.put(id, descriptor);
      }
    }
  }

  public void resetChangesAppliedWithoutRestart() {
    myInstalledWithoutRestartPlugins.clear();
    myUninstalledWithoutRestartPlugins.clear();
  }
}
