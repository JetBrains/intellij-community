/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.ide.extensionResources;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.components.*;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.MapAnnotation;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.util.Map;

@State(name = "ExtensionsRootType", storages = @Storage(value = "extensionsRootType.xml", roamingType = RoamingType.DISABLED))
class ResourceVersions implements PersistentStateComponent<ResourceVersions.State> {

  @NotNull
  public static ResourceVersions getInstance() {
    return ServiceManager.getService(ResourceVersions.class);
  }

  private State myState = new State();

  public boolean shouldUpdateResourcesOf(@NotNull IdeaPluginDescriptor plugin) {
    return myState.isNewOrUpgraded(plugin);
  }

  public void resourcesUpdated(@NotNull IdeaPluginDescriptor of) {
    myState.rememberPlugin(of);
  }

  @Nullable
  @Override
  public State getState() {
    return myState.clone();
  }

  @Override
  public void loadState(State loaded) {
    loaded.forgetDisabledPlugins();
    myState = loaded;
  }


  static class State implements Serializable, Cloneable {
    @Tag("pluginVersions")
    @MapAnnotation(entryTagName = "plugin", keyAttributeName = "id", valueAttributeName = "version",
      surroundWithTag = false, surroundKeyWithTag = false, surroundValueWithTag = false)
    private Map<String, String> myPluginIdToVersion = ContainerUtil.newHashMap();

    public boolean isNewOrUpgraded(@NotNull IdeaPluginDescriptor pluginDescriptor) {
      return !StringUtil.equals(myPluginIdToVersion.get(getId(pluginDescriptor)), getVersion(pluginDescriptor));
    }

    public void rememberPlugin(@NotNull IdeaPluginDescriptor pluginDescriptor) {
      myPluginIdToVersion.put(getId(pluginDescriptor), getVersion(pluginDescriptor));
    }

    public void forgetDisabledPlugins() {
      Map<String, String> newMapping = ContainerUtil.newHashMap();
      for (String pluginIdString : myPluginIdToVersion.keySet()) {
        IdeaPluginDescriptor plugin = PluginManager.getPlugin(PluginId.findId(pluginIdString));
        if (plugin != null && plugin.isEnabled()) {
          newMapping.put(pluginIdString, myPluginIdToVersion.get(pluginIdString));
        }
      }
      myPluginIdToVersion = newMapping;
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

    @NotNull
    private static String getId(@NotNull IdeaPluginDescriptor plugin) {
      PluginId pluginId = plugin.getPluginId();
      return pluginId != null ? pluginId.getIdString() : PluginManagerCore.CORE_PLUGIN_ID;
    }

    @NotNull
    private static String getVersion(@NotNull IdeaPluginDescriptor plugin) {
      if (!plugin.isBundled()) return ObjectUtils.assertNotNull(plugin.getVersion());

      ApplicationInfo appInfo = ApplicationInfo.getInstance();
      BuildNumber build = appInfo.getBuild();
      if (!build.isSnapshot()) return build.asStringWithAllDetails();

      // There is no good way to decide whether to update resources or not when switching to a different development build.
      return build.getProductCode() + "-" + build.getBaselineVersion() + "-" + appInfo.getBuildDate().getTimeInMillis();
    }
  }
}