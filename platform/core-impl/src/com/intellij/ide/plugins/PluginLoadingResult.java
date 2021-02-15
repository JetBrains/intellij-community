// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.core.CoreBundle;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.PlatformUtils;
import com.intellij.util.text.VersionComparatorUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

  private final Map<PluginId, PluginLoadingError> pluginErrors = new ConcurrentHashMap<>();
  private final List<Supplier<@NlsContexts.DetailedDescription String>> globalErrors = Collections.synchronizedList(new ArrayList<>());

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

  @NotNull Map<PluginId, PluginLoadingError> getPluginErrors() {
    return Collections.unmodifiableMap(pluginErrors);
  }

  List<Supplier<@NlsContexts.DetailedDescription String>> getGlobalErrors() {
    synchronized (globalErrors) {
      return new ArrayList<>(globalErrors);
    }
  }

  void addIncompletePlugin(@NotNull IdeaPluginDescriptorImpl plugin, @Nullable PluginLoadingError error) {
    if (!idMap.containsKey(plugin.getPluginId())) {
      incompletePlugins.put(plugin.getPluginId(), plugin);
    }
    if (error != null) {
      pluginErrors.put(plugin.getPluginId(), error);
    }
  }

  void reportIncompatiblePlugin(@NotNull IdeaPluginDescriptorImpl plugin, @NotNull PluginLoadingError error) {
    // do not report if some compatible plugin were already added
    // no race condition here: plugins from classpath are loaded before and not in parallel to loading from plugin dir
    if (idMap.containsKey(plugin.getPluginId())) {
      return;
    }

    error.register(pluginErrors);
  }

  void reportCannotLoad(@NotNull Path file, Exception e) {
    DescriptorListLoadingContext.LOG.warn("Cannot load " + file, e);
    globalErrors.add(() -> CoreBundle.message("plugin.loading.error.text.file.contains.invalid.plugin.descriptor",
                                               FileUtil.getLocationRelativeToUserHome(file.toString(), false)));
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
        PluginLoadingError.create(descriptor,
                                  () -> CoreBundle.message("plugin.loading.error.long.compatible.with.intellij.idea.only", descriptor.getName()),
                                  () -> CoreBundle.message("plugin.loading.error.short.compatible.with.intellij.idea.only")).register(pluginErrors);
        return false;
      }
    }

    // remove any error that occurred for plugin with the same id
    pluginErrors.remove(pluginId);
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
    return PluginManagerCore.checkBuildNumberCompatibility(descriptor, productBuildNumber.get()) == null;
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
