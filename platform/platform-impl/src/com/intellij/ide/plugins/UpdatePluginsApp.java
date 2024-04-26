// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins;

import com.intellij.idea.AppMode;
import com.intellij.openapi.application.ApplicationStarter;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.updateSettings.impl.PluginDownloader;
import com.intellij.openapi.updateSettings.impl.UpdateChecker;
import com.intellij.openapi.updateSettings.impl.UpdateInstaller;
import com.intellij.openapi.util.Ref;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
      LOG.info("updates applied");
      System.exit(0);
    }

    Collection<PluginDownloader> availableUpdates = UpdateChecker.getInternalPluginUpdates()
      .getPluginUpdates()
      .getAllEnabled();
    if (availableUpdates.isEmpty()) {
      LOG.info("all plugins up to date");
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

    LOG.info("Plugins to update: " + pluginsToUpdate);

    Ref<Boolean> installed = Ref.create();
    PluginDownloader.runSynchronouslyInBackground(() -> {
      //noinspection UsagesOfObsoleteApi
      installed.set(UpdateInstaller.installPluginUpdates(pluginsToUpdate, new EmptyProgressIndicator()));
    });

    if (installed.get()) {
      System.exit(0);
    }
    else {
      LOG.warn("Update failed");
      System.exit(1);
    }
  }
}
