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

    pluginsToUpdate = hotfix242PythonSplit(pluginsToUpdate);

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

  private static @NotNull Collection<PluginDownloader> hotfix242PythonSplit(@NotNull Collection<PluginDownloader> pluginsToUpdate) {
    final PluginId pythonidId = PluginId.getId("Pythonid");
    final PluginId pythonCoreId = PluginId.getId("PythonCore");
    if (PluginManagerCore.isPluginInstalled(pythonCoreId)
        || !PluginManagerCore.getBuildNumber().getProductCode().equals("IU")
        || !ContainerUtil.exists(pluginsToUpdate, p -> p.getId().equals(pythonidId))
        || ContainerUtil.exists(pluginsToUpdate, p -> p.getId().equals(pythonCoreId))) {
      return pluginsToUpdate;
    }
    final @NotNull PluginDownloader pythonidDownloader = Objects.requireNonNull(
      ContainerUtil.find(pluginsToUpdate, p -> p.getId().equals(pythonidId))
    );
    if (!ContainerUtil.exists(pythonidDownloader.getDescriptor().getDependencies(), d -> d.getPluginId().equals(pythonCoreId))) {
      logInfo("Plugin Pythonid does not depend on PythonCore");
      return pluginsToUpdate;
    }
    final @Nullable PluginNode pythonCoreNode;
    try {
      pythonCoreNode = ApplicationManager.getApplication().executeOnPooledThread(
        () -> MarketplaceRequests.getInstance().getLastCompatiblePluginUpdate(pythonCoreId)
      ).get();
    }
    catch (InterruptedException | ExecutionException e) {
      LOG.error("Failed to process Pythonid plugin dependencies");
      return pluginsToUpdate;
    }
    if (pythonCoreNode == null) {
      logInfo("Failed to find a suitable PythonCore plugin");
      return pluginsToUpdate;
    }
    final PluginDownloader pythonCoreDownloader;
    try {
      pythonCoreDownloader = PluginDownloader.createDownloader(pythonCoreNode);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    logInfo("Added a required dependency for Pythonid plugin: PythonCore");
    final var result = new ArrayList<>(pluginsToUpdate);
    result.add(pythonCoreDownloader);
    return result;
  }
}
