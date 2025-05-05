// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins;

import com.intellij.diagnostic.LoadingState;
import com.intellij.ide.plugins.marketplace.statistics.PluginManagerUsageCollector;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.updateSettings.impl.PluginDownloader;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * A service to hold a state of plugin changes in a current session (i.e. before the changes are applied on restart).
 */
@Service
public final class InstalledPluginsState {

  public static final @NonNls String RESTART_REQUIRED_MESSAGE = "Not allowing load/unload without restart because of pending restart operation";

  public static @Nullable InstalledPluginsState getInstanceIfLoaded() {
    return LoadingState.COMPONENTS_LOADED.isOccurred() ? getInstance() : null;
  }

  public static InstalledPluginsState getInstance() {
    return ApplicationManager.getApplication().getService(InstalledPluginsState.class);
  }

  private final Object myLock = new Object();
  private final Map<PluginId, IdeaPluginDescriptor> myInstalledPlugins = new IdentityHashMap<>();
  private final Set<PluginId> myInstalledWithoutRestartPlugins = new HashSet<>();
  private final Set<PluginId> myUpdatedPlugins = new HashSet<>();
  private final Set<PluginId> myUpdatedWithoutRestartPlugins = new HashSet<>();
  private final Set<PluginId> myUninstalledWithoutRestartPlugins = new HashSet<>();
  private final Set<String> myOutdatedPlugins = new HashSet<>();
  private boolean myInstallationInProgress = false;
  private boolean myRestartRequired = false;

  private Runnable myShutdownCallback;

  private static List<IdeaPluginDescriptor> myPreInstalledPlugins;

  public static void addPreInstalledPlugin(@NotNull IdeaPluginDescriptor descriptor) {
    if (myPreInstalledPlugins == null) {
      myPreInstalledPlugins = new ArrayList<>();
    }
    myPreInstalledPlugins.add(descriptor);
  }

  public InstalledPluginsState() {
    if (myPreInstalledPlugins != null) {
      for (IdeaPluginDescriptor plugin : myPreInstalledPlugins) {
        if (!PluginManagerCore.isPluginInstalled(plugin.getPluginId())) {
          onPluginInstall(plugin, false, false);
        }
      }
      //noinspection AssignmentToStaticFieldFromInstanceMethod
      myPreInstalledPlugins = null;
    }
  }

  public @NotNull Collection<IdeaPluginDescriptor> getInstalledPlugins() {
    synchronized (myLock) {
      return Collections.unmodifiableCollection(myInstalledPlugins.values());
    }
  }

  public @NotNull Collection<PluginId> getUpdatedPlugins() {
    synchronized (myLock) {
      return Collections.unmodifiableCollection(myUpdatedPlugins);
    }
  }

  public boolean hasNewerVersion(@NotNull PluginId id) {
    synchronized (myLock) {
      return !wasUpdated(id) && myOutdatedPlugins.contains(id.getIdString());
    }
  }

  /**
   * @return whether a plugin with a given id was installed during this IDE session, it required a restart, and it was not an update of an existing plugin.
   */
  public boolean wasInstalled(@NotNull PluginId id) {
    synchronized (myLock) {
      return myInstalledPlugins.containsKey(id);
    }
  }

  public boolean wasInstalledWithoutRestart(@NotNull PluginId id) {
    synchronized (myLock) {
      return myInstalledWithoutRestartPlugins.contains(id);
    }
  }

  public boolean wasUninstalledWithoutRestart(@NotNull PluginId id) {
    synchronized (myLock) {
      return myUninstalledWithoutRestartPlugins.contains(id);
    }
  }

  public boolean wasUpdated(@NotNull PluginId id) {
    synchronized (myLock) {
      return myUpdatedPlugins.contains(id) || myUpdatedWithoutRestartPlugins.contains(id);
    }
  }

  public boolean wasUpdatedWithRestart(@NotNull PluginId id) {
    synchronized (myLock) {
      return myUpdatedPlugins.contains(id);
    }
  }

  /**
   * Should be called whenever a list of plugins is loaded from a repository to check if there is an updated version.
   */
  @ApiStatus.Internal
  public void onDescriptorDownload(@NotNull IdeaPluginDescriptor descriptor) {
    PluginId id = descriptor.getPluginId();
    IdeaPluginDescriptor existing = PluginManagerCore.getPlugin(id);
    if (existing == null || (existing.isBundled() && !existing.allowBundledUpdate()) || wasUpdated(id)) {
      return;
    }

    boolean supersedes = PluginManagerCore.isCompatible(descriptor) &&
                         PluginDownloader.compareVersionsSkipBrokenAndIncompatible(descriptor.getVersion(), existing) > 0;

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
  @ApiStatus.Internal
  public void onPluginInstall(@NotNull IdeaPluginDescriptor descriptor, boolean isUpdate, boolean restartNeeded) {
    PluginId id = descriptor.getPluginId();
    synchronized (myLock) {
      myOutdatedPlugins.remove(id.getIdString());
      if (isUpdate) {
        if (restartNeeded) {
          myUpdatedPlugins.add(id);
        }
        else {
          myUpdatedWithoutRestartPlugins.add(id);
        }
      }
      else if (restartNeeded) {
        myInstalledPlugins.put(id, descriptor);
      }
      else {
        myInstalledWithoutRestartPlugins.add(id);
      }
    }
    PluginManagerUsageCollector.pluginInstallationFinished(descriptor);
  }


  @ApiStatus.Internal
  public void onPluginUninstall(@NotNull IdeaPluginDescriptor descriptor,
                                boolean isDynamicallyUnloadable) {
    PluginId id = descriptor.getPluginId();
    if (isDynamicallyUnloadable) {
      synchronized (myLock) {
        myUninstalledWithoutRestartPlugins.add(id);
      }
    }
    PluginManagerUsageCollector.pluginRemoved(id);
  }

  @ApiStatus.Internal
  public void resetChangesAppliedWithoutRestart() {
    // The plugins configurable may be recreated when installing a plugin that registers any configurables,
    // and this leads to a call of disposeUIResources() that lands here. In this case we must not forget
    // the list of plugins installed/uninstalled without restart (IDEA-233045)
    if (!myInstallationInProgress) {
      myInstalledWithoutRestartPlugins.clear();
      myUninstalledWithoutRestartPlugins.clear();
    }
  }

  @ApiStatus.Internal
  public void trackPluginInstallation(Runnable runnable) {
    myInstallationInProgress = true;
    try {
      runnable.run();
    }
    finally {
      myInstallationInProgress = false;
    }
  }

  @ApiStatus.Internal
  public void setShutdownCallback(Runnable runnable) {
    if (myShutdownCallback == null) {
      myShutdownCallback = runnable;
    }
  }

  @ApiStatus.Internal
  public void clearShutdownCallback() {
    myShutdownCallback = null;
  }

  @ApiStatus.Internal
  public void runShutdownCallback() {
    if (myShutdownCallback != null) {
      myShutdownCallback.run();
      myShutdownCallback = null;
    }
  }

  public boolean isRestartRequired() {
    return myRestartRequired;
  }

  @ApiStatus.Internal
  public void setRestartRequired(boolean restartRequired) {
    myRestartRequired = restartRequired;
  }
}
