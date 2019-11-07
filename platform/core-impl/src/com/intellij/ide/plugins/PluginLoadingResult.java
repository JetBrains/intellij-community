// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.openapi.extensions.PluginId;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@ApiStatus.Internal
final class PluginLoadingResult {
  final List<IdeaPluginDescriptorImpl> plugins = new ArrayList<>();
  final List<IdeaPluginDescriptorImpl> pluginsWithoutId = new ArrayList<>();

  private final Set<IdeaPluginDescriptorImpl> existingResults = new HashSet<>();

  @Nullable
  private Map<PluginId, IdeaPluginDescriptorImpl> duplicateMap = null;

  final List<String> errors = new ArrayList<>();

  private IdeaPluginDescriptorImpl[] sortedPlugins;
  private List<IdeaPluginDescriptorImpl> sortedEnabledPlugins;
  private Set<PluginId> effectiveDisabledIds;
  private Set<PluginId> disabledRequiredIds;

  Map<PluginId, Set<String>> brokenPluginVersions;

  PluginLoadingResult(@NotNull Map<PluginId, Set<String>> brokenPluginVersions) {
    this.brokenPluginVersions = brokenPluginVersions;
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

    if (duplicateMap != null) {
      duplicateMap = null;
    }
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

  boolean add(@NotNull IdeaPluginDescriptorImpl descriptor, boolean silentlyIgnoreIfDuplicate) {
    PluginId id = descriptor.getPluginId();
    if (id == null) {
      pluginsWithoutId.add(descriptor);
      errors.add("No id is provided by \"" + descriptor.getPluginPath().getFileName().toString() + "\"");
      return true;
    }

    if (!descriptor.isBundled()) {
      Set<String> set = brokenPluginVersions.get(descriptor.getPluginId());
      if (set != null && set.contains(descriptor.getVersion())) {
        errors.add("Version " + descriptor.getVersion() + " was marked as incompatible for " + descriptor);
        return true;
      }
    }

    if (!silentlyIgnoreIfDuplicate && duplicateMap != null) {
      IdeaPluginDescriptorImpl existing = duplicateMap.get(id);
      if (existing != null) {
        errors.add(descriptor + " duplicates " + existing);
        return false;
      }
    }

    if (existingResults.add(descriptor)) {
      plugins.add(descriptor);
      return true;
    }

    if (silentlyIgnoreIfDuplicate) {
      return false;
    }

    int index = plugins.indexOf(descriptor);
    // unrealistic case, but still
    if (index == -1) {
      errors.add("internal error: cannot find duplicated descriptor for " + descriptor);
    }
    else {
      IdeaPluginDescriptorImpl existing = plugins.remove(index);
      if (duplicateMap == null) {
        duplicateMap = new HashMap<>();
      }

      duplicateMap.put(id, existing);
      existingResults.remove(descriptor);
      errors.add(descriptor + " duplicates " + existing);
    }
    return false;
  }

  void replace(int prevIndex, @NotNull IdeaPluginDescriptorImpl oldDescriptor, @NotNull IdeaPluginDescriptorImpl newDescriptor) {
    existingResults.remove(oldDescriptor);
    existingResults.add(newDescriptor);
    plugins.set(prevIndex, newDescriptor);
  }
}
