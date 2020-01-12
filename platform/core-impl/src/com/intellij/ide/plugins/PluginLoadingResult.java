// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.util.PlatformUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ContainerUtilRt;
import com.intellij.util.text.VersionComparatorUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@ApiStatus.Internal
final class PluginLoadingResult {
  final Map<PluginId, Set<String>> brokenPluginVersions;
  @NotNull
  final BuildNumber productBuildNumber;

  final Map<PluginId, IdeaPluginDescriptorImpl> incompletePlugins = ContainerUtil.newConcurrentMap();

  final List<IdeaPluginDescriptorImpl> pluginsWithoutId = new ArrayList<>();
  private final Map<PluginId, IdeaPluginDescriptorImpl> plugins = new HashMap<>();

  // only read is concurrent, write from the only thread
  final Map<PluginId, IdeaPluginDescriptorImpl> idMap = ContainerUtil.newConcurrentMap();

  @Nullable
  Map<PluginId, List<IdeaPluginDescriptorImpl>> duplicateModuleMap;

  final Map<PluginId, String> errors = ContainerUtil.newConcurrentMap();

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

  int enabledPluginCount() {
    return plugins.size();
  }

  /**
   * not null after initialization ({@link PluginManagerCore#initializePlugins})
   */
  IdeaPluginDescriptorImpl @NotNull [] getSortedPlugins() {
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

  IdeaPluginDescriptorImpl @NotNull [] finishLoading() {
    IdeaPluginDescriptorImpl[] enabledPlugins = plugins.values().toArray(IdeaPluginDescriptorImpl.EMPTY_ARRAY);
    plugins.clear();
    Arrays.sort(enabledPlugins, Comparator.comparing(IdeaPluginDescriptorImpl::getPluginId));
    return enabledPlugins;
  }

  @NotNull
  List<String> getErrors() {
    if (errors.isEmpty()) {
      return Collections.emptyList();
    }

    PluginId[] ids = errors.keySet().toArray(PluginId.EMPTY_ARRAY);
    Arrays.sort(ids, null);
    List<String> result = new ArrayList<>(ids.length);
    for (PluginId id : ids) {
      result.add(errors.get(id));
    }
    return result;
  }

  void finishInitializing(IdeaPluginDescriptorImpl @NotNull [] sortedPlugins,
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
  boolean add(@NotNull IdeaPluginDescriptorImpl descriptor, @NotNull DescriptorListLoadingContext context) {
    PluginId pluginId = descriptor.getPluginId();
    if (pluginId == null) {
      pluginsWithoutId.add(descriptor);
      context.getLogger().warn("No id is provided by \"" + descriptor.getPluginPath().getFileName().toString() + "\"");
      return true;
    }

    if (descriptor.incomplete) {
      return true;
    }

    if (!descriptor.isBundled()) {
      Set<String> set = brokenPluginVersions.get(pluginId);
      if (set != null && set.contains(descriptor.getVersion())) {
        String message = descriptor.formatErrorMessage("was marked as incompatible");
        context.getLogger().info(message);
        errors.put(pluginId, message);
        return true;
      }

      if (checkModuleDependencies && !PluginManagerCore.hasModuleDependencies(descriptor)) {
        String message = descriptor.formatErrorMessage("defines no module dependencies (supported only in IntelliJ IDEA)");
        context.getLogger().info(message);
        errors.put(pluginId, message);
        return false;
      }
    }

    IdeaPluginDescriptorImpl prevDescriptor = plugins.put(pluginId, descriptor);
    if (prevDescriptor == null) {
      idMap.put(pluginId, descriptor);

      for (PluginId module : descriptor.getModules()) {
        checkAndAdd(descriptor, module);
      }
      return true;
    }

    if (isCompatible(descriptor) && VersionComparatorUtil.compare(descriptor.getVersion(), prevDescriptor.getVersion()) > 0) {
      context.getLogger().info(descriptor.getPluginPath() + " overrides " + prevDescriptor.getPluginPath());
      idMap.put(pluginId, descriptor);
      return true;
    }
    else {
      plugins.put(pluginId, prevDescriptor);
      return false;
    }
  }

  @Contract(pure = true)
  boolean contains(@NotNull IdeaPluginDescriptorImpl descriptor) {
    PluginId pluginId = descriptor.getPluginId();
    return (pluginId != null && plugins.containsKey(pluginId));
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
}
