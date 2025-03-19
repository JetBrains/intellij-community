// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.extensionResources;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.XMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@State(name = "ExtensionsRootType", storages = @Storage(StoragePathMacros.CACHE_FILE))
final class ResourceVersions implements PersistentStateComponent<ResourceVersions.State> {
  public static @NotNull ResourceVersions getInstance() {
    return ApplicationManager.getApplication().getService(ResourceVersions.class);
  }

  private State myState = new State();

  public boolean shouldUpdateResourcesOf(@NotNull IdeaPluginDescriptor plugin) {
    return myState.isNewOrUpgraded(plugin);
  }

  public void resourcesUpdated(@NotNull IdeaPluginDescriptor plugin) {
    myState.rememberPlugin(plugin);
  }

  @Override
  public @Nullable State getState() {
    return myState.clone();
  }

  @Override
  public void loadState(@NotNull State loaded) {
    myState = State.forgetDisabledPlugins(loaded);
  }

  static final class State implements Serializable, Cloneable {
    @Tag("pluginVersions")
    @XMap(entryTagName = "plugin", keyAttributeName = "id", valueAttributeName = "version")
    private final Map<String, String> pluginIdToVersion;

    State() {
      pluginIdToVersion = new HashMap<>();
    }

    private State(@NotNull Map<String, String> pluginIdToVersion) {
      this.pluginIdToVersion = pluginIdToVersion;
    }

    public boolean isNewOrUpgraded(@NotNull IdeaPluginDescriptor pluginDescriptor) {
      synchronized (pluginIdToVersion) {
        return !StringUtil.equals(pluginIdToVersion.get(getId(pluginDescriptor)), getVersion(pluginDescriptor));
      }
    }

    public void rememberPlugin(@NotNull IdeaPluginDescriptor pluginDescriptor) {
      synchronized (pluginIdToVersion) {
        pluginIdToVersion.put(getId(pluginDescriptor), getVersion(pluginDescriptor));
      }
    }

    public static @NotNull State forgetDisabledPlugins(@NotNull State storedState) {
      synchronized (storedState.pluginIdToVersion) {
        Map<String, String> pluginIdToVersion = new HashMap<>(storedState.pluginIdToVersion.size());
        for (String pluginIdString : storedState.pluginIdToVersion.keySet()) {
          PluginId pluginId = PluginId.findId(pluginIdString);
          IdeaPluginDescriptor plugin = pluginId == null ? null : PluginManager.getInstance().findEnabledPlugin(pluginId);
          if (plugin != null) {
            pluginIdToVersion.put(pluginIdString, storedState.pluginIdToVersion.get(pluginIdString));
          }
        }
        return new State(pluginIdToVersion);
      }
    }

    @Override
    public State clone() {
      try {
        return (State)super.clone();
      }
      catch (CloneNotSupportedException e) {
        ExtensionsRootType.LOG.error(e);
      }
      return null;
    }

    private static @NotNull String getId(@NotNull IdeaPluginDescriptor plugin) {
      return plugin.getPluginId().getIdString();
    }

    private static @NotNull String getVersion(@NotNull IdeaPluginDescriptor plugin) {
      if (!plugin.isBundled()) {
        return Objects.requireNonNull(plugin.getVersion());
      }

      ApplicationInfo appInfo = ApplicationInfo.getInstance();
      BuildNumber build = appInfo.getBuild();
      if (!build.isSnapshot()) {
        return build.asString();
      }

      // there is no good way to decide whether to update resources or not when switching to a different development build.
      return build.getProductCode() + "-" + build.getBaselineVersion() + "-" + appInfo.getBuildDate().getTimeInMillis();
    }
  }
}