// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.openapi.extensions.PluginId;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@ApiStatus.Internal
final class LoadPluginResult {
  final List<IdeaPluginDescriptorImpl> plugins = new ArrayList<>();
  final List<IdeaPluginDescriptorImpl> brokenPlugins = new ArrayList<>();

  private final Set<IdeaPluginDescriptorImpl> existingResults = new HashSet<>();

  @Nullable
  private Map<PluginId, IdeaPluginDescriptorImpl> duplicateMap = null;

  final List<String> errors = new ArrayList<>();

  private IdeaPluginDescriptorImpl[] sortedPlugins;
  private List<IdeaPluginDescriptorImpl> sortedEnabledPlugins;

  /**
   * not null after initialization ({@link PluginManagerCore#initializePlugins})
   */
  @NotNull
  IdeaPluginDescriptorImpl[] getSortedPlugins() {
    return sortedPlugins;
  }

  void setSortedPlugins(@NotNull IdeaPluginDescriptorImpl[] value) {
    assert sortedPlugins == null;
    this.sortedPlugins = value;
  }

  @NotNull
  List<IdeaPluginDescriptorImpl> getSortedEnabledPlugins() {
    return sortedEnabledPlugins;
  }

  void setSortedEnabledPlugins(@NotNull List<IdeaPluginDescriptorImpl> value) {
    assert sortedEnabledPlugins == null;
    sortedEnabledPlugins = value;
  }

  void finish() {
    existingResults.clear();
    plugins.sort(Comparator.comparing(IdeaPluginDescriptorImpl::getPluginId));

    if (duplicateMap != null) {
      duplicateMap = null;
    }
  }

  boolean add(@NotNull IdeaPluginDescriptorImpl descriptor, boolean silentlyIgnoreIfDuplicate) {
    PluginId id = descriptor.getPluginId();
    if (id == null) {
      brokenPlugins.add(descriptor);
      errors.add("No id is provided by \"" + descriptor.getPluginPath().getFileName().toString() + "\"");
      return true;
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
