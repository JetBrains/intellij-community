// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins;

import com.intellij.ide.plugins.marketplace.MarketplaceRequests;
import com.intellij.idea.AppMode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationStarter;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.updateSettings.impl.InternalPluginResults;
import com.intellij.openapi.updateSettings.impl.PluginDownloader;
import com.intellij.openapi.updateSettings.impl.UpdateChecker;
import com.intellij.openapi.updateSettings.impl.UpdateInstaller;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutionException;

/**
 * Works in two stages.
 * On the first run, it collects available updates and writes an update script.
 * The second run needs {@code idea.force.plugin.updates = "true"} system property to apply the updates.
 *
 * @see AppMode#FORCE_PLUGIN_UPDATES
 * @see com.intellij.idea.Main#installPluginUpdates
 */
final class UpdatePluginsApp implements ApplicationStarter {
  private static final Logger LOG = Logger.getInstance(UpdatePluginsApp.class);
  private static final String OLD_CONFIG_DIR_PROPERTY = "idea.plugin.migration.config.dir";

  @Override
  public void premain(@NotNull List<String> args) {
    System.setProperty("idea.skip.indices.initialization", "true");

    var oldConfig = System.getProperty(OLD_CONFIG_DIR_PROPERTY);
    if (oldConfig != null) {
      RepositoryHelper.updatePluginHostsFromConfigDir(Path.of(oldConfig), LOG);
    }
  }

  @Override
  public void main(@NotNull List<String> args) {
    if (Boolean.getBoolean(AppMode.FORCE_PLUGIN_UPDATES)) {
      logInfo("Plugin updates are applied");
      System.exit(0);
    }

    final InternalPluginResults updateCheckResult;
    final Collection<PluginDownloader> availableUpdates;
    try {
      updateCheckResult = ApplicationManager.getApplication().executeOnPooledThread(
        () -> UpdateChecker.getInternalPluginUpdates()
      ).get();
    }
    catch (InterruptedException | ExecutionException e) {
      LOG.error("Failed to check plugin updates", e);
      System.exit(1);
      return;
    }
    if (!updateCheckResult.getErrors().isEmpty()) {
      LOG.warn("Errors occurred during the update check: " +
               ContainerUtil.map(updateCheckResult.getErrors().entrySet(), entry -> "host=" + entry.getKey() + ": " + entry.getValue().getMessage()));
    }

    availableUpdates = updateCheckResult.getPluginUpdates().getAllEnabled();
    if (availableUpdates.isEmpty()) {
      logInfo("all plugins up to date");
      System.exit(0);
      return;
    }
    Collection<PluginDownloader> pluginsToUpdate;
    if (args.size() > 1) {
      Set<String> filter = new HashSet<>(args.subList(1, args.size()));
      pluginsToUpdate = availableUpdates.stream()
        .filter(downloader -> filter.contains(downloader.getId().getIdString()))
        .toList();
    }
    else {
      pluginsToUpdate = availableUpdates;
    }

    pluginsToUpdate = hotfix242InstallDependency(pluginsToUpdate, PluginId.getId("Pythonid"), PluginId.getId("PythonCore"));
    pluginsToUpdate = hotfix242InstallDependency(pluginsToUpdate, PluginId.getId("intellij.jupyter"), PluginId.getId("com.intellij.notebooks.core"));
    pluginsToUpdate = hotfix242InstallDependency(pluginsToUpdate, PluginId.getId("R4Intellij"), PluginId.getId("com.intellij.notebooks.core"));

    logInfo("Plugins to update: " +
            ContainerUtil.map(pluginsToUpdate, downloader -> downloader.getPluginName() + " version " + downloader.getPluginVersion()));

    final boolean installed;
    try {
      final var finalPluginsToUpdate = pluginsToUpdate;
      installed = ApplicationManager.getApplication().executeOnPooledThread(
        () -> UpdateInstaller.installPluginUpdates(finalPluginsToUpdate, new EmptyProgressIndicator())
      ).get();
    }
    catch (InterruptedException | ExecutionException e) {
      LOG.error("Failed to install plugin updates", e);
      System.exit(1);
      return;
    }

    if (installed) {
      logInfo("Plugin updates are prepared to be installed");
      System.exit(0);
    }
    else {
      LOG.warn("Update failed");
      System.exit(1);
    }
  }

  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  private static void logInfo(String msg) {
    // INFO level messages are not printed to stdout/stderr and toolbox does not include stdout/stderr by default in logs
    System.out.println(msg);
    LOG.info(msg);
  }

  private static @NotNull Collection<PluginDownloader> hotfix242InstallDependency(@NotNull Collection<PluginDownloader> pluginsToUpdate,
                                                                                  PluginId pluginId,
                                                                                  PluginId dependencyId) {
    if (PluginManagerCore.isPluginInstalled(dependencyId)
        || !ContainerUtil.exists(pluginsToUpdate, p -> p.getId().equals(pluginId))
        || ContainerUtil.exists(pluginsToUpdate, p -> p.getId().equals(dependencyId))) {
      return pluginsToUpdate;
    }
    final @NotNull PluginDownloader pluginDownloader = Objects.requireNonNull(
      ContainerUtil.find(pluginsToUpdate, p -> p.getId().equals(pluginId))
    );
    if (!ContainerUtil.exists(pluginDownloader.getDescriptor().getDependencies(), d -> d.getPluginId().equals(dependencyId))) {
      logInfo("Plugin " + pluginId + " does not depend on " + dependencyId);
      return pluginsToUpdate;
    }
    final @Nullable PluginNode dependencyNode;
    try {
      dependencyNode = ApplicationManager.getApplication().executeOnPooledThread(
        () -> MarketplaceRequests.getInstance().getLastCompatiblePluginUpdate(dependencyId)
      ).get();
    }
    catch (InterruptedException | ExecutionException e) {
      LOG.error("Failed to process " + pluginId + " plugin dependencies");
      return pluginsToUpdate;
    }
    if (dependencyNode == null) {
      logInfo("Failed to find a suitable " + dependencyId + " plugin");
      return pluginsToUpdate;
    }
    final PluginDownloader dependencyDownloader;
    try {
      dependencyDownloader = PluginDownloader.createDownloader(dependencyNode);
    }
    catch (IOException e) {
      LOG.error("Failed to create a plugin downloader", e);
      return pluginsToUpdate;
    }
    logInfo("Added a required dependency for " + pluginId + " plugin for installation: " + dependencyId);
    final var result = new ArrayList<>(pluginsToUpdate);
    result.add(dependencyDownloader);
    return result;
  }
}
