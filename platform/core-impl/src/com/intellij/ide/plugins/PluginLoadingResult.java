// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.PlatformUtils;
import com.intellij.util.text.VersionComparatorUtil;
import org.jetbrains.annotations.*;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@ApiStatus.Internal
final class PluginLoadingResult {
  private final Map<PluginId, Set<String>> brokenPluginVersions;
  final @NotNull Supplier<BuildNumber> productBuildNumber;

  final Map<PluginId, IdeaPluginDescriptorImpl> incompletePlugins = new ConcurrentHashMap<>();

  private final Map<PluginId, IdeaPluginDescriptorImpl> plugins = new HashMap<>();

  // only read is concurrent, write from the only thread
  final Map<PluginId, IdeaPluginDescriptorImpl> idMap = new ConcurrentHashMap<>();

  @Nullable Map<PluginId, List<IdeaPluginDescriptorImpl>> duplicateModuleMap;

  private final Map<PluginId, PluginError> errors = new ConcurrentHashMap<>();

  private final Set<PluginId> shadowedBundledIds = new HashSet<>();

  private final boolean checkModuleDependencies;

  // result, after calling finishLoading
  private List<IdeaPluginDescriptorImpl> enabledPlugins;

  @NotNull List<IdeaPluginDescriptorImpl> getEnabledPlugins() {
    return enabledPlugins;
  }

  PluginLoadingResult(@NotNull Map<PluginId, Set<String>> brokenPluginVersions, @NotNull Supplier<BuildNumber> productBuildNumber) {
    this(brokenPluginVersions, productBuildNumber, !PlatformUtils.isIntelliJ());
  }

  PluginLoadingResult(@NotNull Map<PluginId, Set<String>> brokenPluginVersions, @NotNull Supplier<BuildNumber> productBuildNumber, boolean checkModuleDependencies) {
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

  void finishLoading() {
    IdeaPluginDescriptorImpl[] enabledPlugins = this.plugins.values().toArray(IdeaPluginDescriptorImpl.EMPTY_ARRAY);
    this.plugins.clear();
    Arrays.sort(enabledPlugins, Comparator.comparing(IdeaPluginDescriptorImpl::getPluginId));
    this.enabledPlugins = Arrays.asList(enabledPlugins);
  }

  boolean isBroken(@NotNull PluginId id) {
    Set<String> set = brokenPluginVersions.get(id);
    if (set == null) {
      return false;
    }

    IdeaPluginDescriptorImpl descriptor = idMap.get(id);
    return descriptor != null && set.contains(descriptor.getVersion());
  }

  @NotNull List<PluginError> getErrors() {
    if (errors.isEmpty()) {
      return Collections.emptyList();
    }

    PluginId[] ids = errors.keySet().toArray(PluginId.EMPTY_ARRAY);
    Arrays.sort(ids, null);
    List<PluginError> result = new ArrayList<>(ids.length);
    for (PluginId id : ids) {
      result.add(errors.get(id));
    }
    return result;
  }

  void addIncompletePlugin(@NotNull IdeaPluginDescriptorImpl plugin, @Nullable PluginError error) {
    if (!idMap.containsKey(plugin.getPluginId())) {
      incompletePlugins.put(plugin.getPluginId(), plugin);
    }
    if (error != null) {
      errors.put(plugin.getPluginId(), error);
    }
  }

  void reportIncompatiblePlugin(@NotNull IdeaPluginDescriptorImpl plugin, @NotNull @Nls String reason, 
                                @Nullable @NonNls String since, @Nullable @NonNls String until) {
    // do not report if some compatible plugin were already added
    // no race condition here — plugins from classpath are loaded before and not in parallel to loading from plugin dir
    if (idMap.containsKey(plugin.getPluginId())) {
      return;
    }

    if (since == null) {
      since = "0.0";
    }
    if (until == null) {
      until = "*.*";
    }

    String message = "is incompatible (reason: " + reason + ", target build " +
                     (since.equals(until) ? ("is " + since) : ("range is " + since + " to " + until)) +
                     ")";
    errors.put(plugin.getPluginId(), new PluginError(plugin, message, reason));
  }

  void reportCannotLoad(@NotNull Path file, Exception e) {
    DescriptorListLoadingContext.LOG.warn("Cannot load " + file, e);
    errors.put(PluginId.getId("__cannot load__"), new PluginError(null, "File \"" + FileUtil.getLocationRelativeToUserHome(file.toString(), false) + "\" contains invalid plugin descriptor", null));
  }

  @SuppressWarnings("UnusedReturnValue")
  boolean add(@NotNull IdeaPluginDescriptorImpl descriptor, boolean overrideUseIfCompatible) {
    PluginId pluginId = descriptor.getPluginId();
    if (pluginId == null) {
      PluginManagerCore.getLogger().warn("No id is provided by \"" + descriptor.getPluginPath().getFileName().toString() + "\"");
      return true;
    }

    if (descriptor.incomplete) {
      return true;
    }

    if (!descriptor.isBundled()) {
      if (checkModuleDependencies && !PluginManagerCore.hasModuleDependencies(descriptor)) {
        String message = "defines no module dependencies (supported only in IntelliJ IDEA)";
        errors.put(pluginId, new PluginError(descriptor, message, "supported only in IntelliJ IDEA"));
        return false;
      }
    }

    // remove any error that occurred for plugin with the same id
    errors.remove(pluginId);
    incompletePlugins.remove(pluginId);

    IdeaPluginDescriptorImpl prevDescriptor = plugins.put(pluginId, descriptor);
    if (prevDescriptor == null) {
      idMap.put(pluginId, descriptor);

      for (PluginId module : descriptor.getModules()) {
        checkAndAdd(descriptor, module);
      }
      return true;
    }

    if (prevDescriptor.isBundled() || descriptor.isBundled()) {
      shadowedBundledIds.add(pluginId);
    }

    if (isCompatible(descriptor) &&
        (overrideUseIfCompatible || VersionComparatorUtil.compare(descriptor.getVersion(), prevDescriptor.getVersion()) > 0)) {
      PluginManagerCore.getLogger().info(descriptor.getPluginPath() + " overrides " + prevDescriptor.getPluginPath());
      idMap.put(pluginId, descriptor);
      return true;
    }
    else {
      plugins.put(pluginId, prevDescriptor);
      return false;
    }
  }

  private boolean isCompatible(@NotNull IdeaPluginDescriptorImpl descriptor) {
    return PluginManagerCore.getIncompatibleMessage(productBuildNumber.get(), descriptor.getSinceBuild(), descriptor.getUntilBuild()) == null;
  }

  @SuppressWarnings("DuplicatedCode")
  private void checkAndAdd(@NotNull IdeaPluginDescriptorImpl descriptor, @NotNull PluginId id) {
    if (duplicateModuleMap != null) {
      List<IdeaPluginDescriptorImpl> duplicates = duplicateModuleMap.get(id);
      if (duplicates != null) {
        duplicates.add(descriptor);
        return;
      }
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

  Set<PluginId> getShadowedBundledIds() {
    return shadowedBundledIds;
  }
}
