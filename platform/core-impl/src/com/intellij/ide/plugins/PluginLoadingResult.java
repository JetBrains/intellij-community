// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.util.PlatformUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ContainerUtilRt;
import com.intellij.util.text.VersionComparatorUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

@ApiStatus.Internal
final class PluginLoadingResult {
  final Map<PluginId, Set<String>> brokenPluginVersions;
  @NotNull
  final BuildNumber productBuildNumber;

  final List<IdeaPluginDescriptorImpl> plugins = new ArrayList<>();
  final Map<PluginId, IdeaPluginDescriptorImpl> incompletePlugins = ContainerUtil.newConcurrentMap();
  final List<IdeaPluginDescriptorImpl> pluginsWithoutId = new ArrayList<>();

  private final Set<IdeaPluginDescriptorImpl> existingResults = new HashSet<>();

  // only read is concurrent, write from the only thread
  final Map<PluginId, IdeaPluginDescriptorImpl> idMap = ContainerUtil.newConcurrentMap();

  @Nullable
  Map<PluginId, List<IdeaPluginDescriptorImpl>> duplicateModuleMap;

  final Collection<String> errors = new ConcurrentLinkedQueue<>();

  private IdeaPluginDescriptorImpl[] sortedPlugins;
  private List<IdeaPluginDescriptorImpl> sortedEnabledPlugins;
  private Set<PluginId> effectiveDisabledIds;
  private Set<PluginId> disabledRequiredIds;

  final boolean checkModuleDependencies;

  PluginLoadingResult(@NotNull Map<PluginId, Set<String>> brokenPluginVersions, @NotNull BuildNumber productBuildNumber) {
    this(brokenPluginVersions, productBuildNumber, !PlatformUtils.isIntelliJ());
  }

  PluginLoadingResult(@NotNull Map<PluginId, Set<String>> brokenPluginVersions, @NotNull BuildNumber productBuildNumber, boolean checkModuleDependencies) {
    this.brokenPluginVersions = brokenPluginVersions;
    this.productBuildNumber = productBuildNumber;

    // https://www.jetbrains.org/intellij/sdk/docs/basics/getting_started/plugin_compatibility.html
    // If a plugin does not include any module dependency tags in its plugin.xml,
    // it's assumed to be a legacy plugin and is loaded only in IntelliJ IDEA.
    this.checkModuleDependencies = checkModuleDependencies;
  }

  /**
   * not null after initialization ({@link PluginManagerCore#initializePlugins})
   */
  @NotNull
  IdeaPluginDescriptorImpl[] getSortedPlugins() {
    return sortedPlugins;
  }

  @NotNull
  List<IdeaPluginDescriptorImpl> getSortedEnabledPlugins() {
    return sortedEnabledPlugins;
  }

  @NotNull
  Set<PluginId> getEffectiveDisabledIds() {
    return effectiveDisabledIds;
  }

  @NotNull
  Set<PluginId> getDisabledRequiredIds() {
    return disabledRequiredIds;
  }

  void finishLoading() {
    existingResults.clear();
    plugins.sort(Comparator.comparing(IdeaPluginDescriptorImpl::getPluginId));
  }

  void finishInitializing(@NotNull IdeaPluginDescriptorImpl[] sortedPlugins,
                          @NotNull List<IdeaPluginDescriptorImpl> sortedEnabledPlugins,
                          @NotNull Map<PluginId, String> disabledIds,
                          @NotNull Set<PluginId> disabledRequiredIds) {
    assert this.sortedPlugins == null && this.sortedEnabledPlugins == null && effectiveDisabledIds == null;

    this.sortedPlugins = sortedPlugins;
    this.sortedEnabledPlugins = sortedEnabledPlugins;
    effectiveDisabledIds = disabledIds.isEmpty() ? Collections.emptySet() : new HashSet<>(disabledIds.keySet());
    this.disabledRequiredIds = disabledRequiredIds;
  }

  @SuppressWarnings("UnusedReturnValue")
  boolean add(@NotNull IdeaPluginDescriptorImpl descriptor) {
    PluginId pluginId = descriptor.getPluginId();
    if (pluginId == null) {
      pluginsWithoutId.add(descriptor);
      errors.add("No id is provided by \"" + descriptor.getPluginPath().getFileName().toString() + "\"");
      return true;
    }

    if (descriptor.incomplete) {
      return true;
    }

    if (!descriptor.isBundled()) {
      Set<String> set = brokenPluginVersions.get(pluginId);
      if (set != null && set.contains(descriptor.getVersion())) {
        errors.add("Version " + descriptor.getVersion() + " was marked as incompatible for " + descriptor);
        return true;
      }

      if (checkModuleDependencies && !PluginManagerCore.hasModuleDependencies(descriptor)) {
        errors.add("Plugin " + descriptor + " defines no module dependencies (supported only in IntelliJ IDEA)");
        return false;
      }
    }

    if (existingResults.add(descriptor)) {
      plugins.add(descriptor);
      idMap.put(pluginId, descriptor);

      for (PluginId module : descriptor.getModules()) {
        checkAndAdd(descriptor, module);
      }
      return true;
    }

    int prevIndex = plugins.indexOf(descriptor);
    IdeaPluginDescriptorImpl prevDescriptor = plugins.get(prevIndex);
    boolean compatible = isCompatible(descriptor);
    boolean prevCompatible = isCompatible(prevDescriptor);
    boolean newer = VersionComparatorUtil.compare(descriptor.getVersion(), prevDescriptor.getVersion()) > 0;
    if ((compatible && !prevCompatible) || (compatible == prevCompatible && newer)) {
      replace(prevIndex, prevDescriptor, descriptor);
      PluginManagerCore.getLogger().info(descriptor.getPath() + " overrides " + prevDescriptor.getPath());
      idMap.put(pluginId, descriptor);
      return true;
    }
    else {
      return false;
    }
  }

  private boolean isCompatible(@NotNull IdeaPluginDescriptorImpl descriptor) {
    return PluginManagerCore.isIncompatible(productBuildNumber, descriptor.getSinceBuild(), descriptor.getUntilBuild()) == null;
  }

  @SuppressWarnings("DuplicatedCode")
  private void checkAndAdd(@NotNull IdeaPluginDescriptorImpl descriptor, @NotNull PluginId id) {
    if (duplicateModuleMap != null && duplicateModuleMap.containsKey(id)) {
      ContainerUtilRt.putValue(id, descriptor, duplicateModuleMap);
      return;
    }

    IdeaPluginDescriptorImpl existingDescriptor = idMap.put(id, descriptor);
    if (existingDescriptor == null) {
      return;
    }

    // if duplicated, both are removed
    idMap.remove(id);
    if (duplicateModuleMap == null) {
      duplicateModuleMap = new LinkedHashMap<>();
    }

    List<IdeaPluginDescriptorImpl> list = new ArrayList<>();
    list.add(existingDescriptor);
    list.add(descriptor);
    duplicateModuleMap.put(id, list);
  }

  private void replace(int prevIndex, @NotNull IdeaPluginDescriptorImpl oldDescriptor, @NotNull IdeaPluginDescriptorImpl newDescriptor) {
    existingResults.remove(oldDescriptor);
    existingResults.add(newDescriptor);
    plugins.set(prevIndex, newDescriptor);
  }
}
