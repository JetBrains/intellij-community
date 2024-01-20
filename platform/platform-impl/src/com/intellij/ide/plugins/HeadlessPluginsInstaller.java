// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins;

import com.intellij.externalDependencies.DependencyOnPlugin;
import com.intellij.externalDependencies.ExternalDependenciesManager;
import com.intellij.ide.impl.OpenProjectTaskKt;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.application.ApplicationStarter;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.updateSettings.impl.PluginDownloader;
import com.intellij.util.io.URLUtil;
import kotlin.Unit;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.*;

@SuppressWarnings("UseOfSystemOutOrSystemErr")
public final class HeadlessPluginsInstaller implements ApplicationStarter {
  private static final Logger LOG = Logger.getInstance(HeadlessPluginsInstaller.class);

  @Override
  @SuppressWarnings("deprecation")
  public @NotNull String getCommandName() {
    return "installPlugins";
  }

  @Override
  public int getRequiredModality() {
    return NOT_IN_EDT;
  }

  @Override
  public void main(@NotNull List<String> args) {
    try {
      var pluginIds = new LinkedHashSet<PluginId>();
      var customRepositories = new LinkedHashSet<String>();
      var projectPaths = new ArrayList<Path>();

      for (int i = 1; i < args.size(); i++) {
        var arg = args.get(i);
        if ("-h".equals(arg) || "--help".equals(arg)) {
          System.out.println(
            """
              Usage: installPlugins pluginId* repository* (--for-project=<project-path>)*

              Installs plugins with `pluginId` from the Marketplace or provided `repository`-es.
              If `--for-project` is specified, also installs the required plugins for a project located at <project-path>.""");
        }
        else if (arg.startsWith("--for-project=")) {
          projectPaths.add(Path.of(arg.replace("--for-project=", "")));
        }
        else if (arg.contains(URLUtil.SCHEME_SEPARATOR)) {
          customRepositories.add(arg);
        }
        else {
          pluginIds.add(PluginId.getId(arg));
        }
      }

      collectProjectRequiredPlugins(pluginIds, projectPaths);

      if (!customRepositories.isEmpty()) {
        var hosts = System.getProperty("idea.plugin.hosts");
        var newHosts = String.join(";", customRepositories);
        if (hosts != null && !hosts.isBlank()) {
          newHosts = hosts + ";" + newHosts;
        }
        System.setProperty("idea.plugin.hosts", newHosts);
        LOG.info("plugin hosts: " + newHosts);
      }

      LOG.info("installing: " + pluginIds);
      var installed = installPlugins(pluginIds);
      System.exit(installed.size() == pluginIds.size() ? 0 : 1);
    }
    catch (Throwable t) {
      LOG.error(t);
      System.exit(1);
    }
  }

  private static void collectProjectRequiredPlugins(Collection<PluginId> collector, List<Path> projectPaths) {
    var options = OpenProjectTaskKt.OpenProjectTask(builder -> {
      builder.setShowWelcomeScreen(false);
      builder.setRunConversionBeforeOpen(false);
      return Unit.INSTANCE;
    });
    for (var path : projectPaths) {
      var project = ProjectUtil.openOrImport(path, options);
      if (project == null) {
        LOG.error("Cannot open a project at " + path);
        System.exit(1);
      }
      for (var dependency : ExternalDependenciesManager.getInstance(project).getDependencies(DependencyOnPlugin.class)) {
        collector.add(PluginId.getId(dependency.getPluginId()));
      }
      ProjectManagerEx.getInstanceEx().forceCloseProject(project);
    }
  }

  public static @NotNull Collection<PluginNode> installPlugins(@NotNull Set<PluginId> pluginIds) {
    var plugins = RepositoryHelper.loadPlugins(pluginIds);

    if (!PluginManagerMain.checkThirdPartyPluginsAllowed(plugins)) {
      LOG.info("3rd-party plugins rejected");
      return Collections.emptyList();
    }

    if (plugins.size() < pluginIds.size()) {
      var unknown = new HashSet<>(pluginIds);
      for (var plugin : plugins) unknown.remove(plugin.getPluginId());
      LOG.info("unknown plugins: " + unknown);
    }

    var indicator = new EmptyProgressIndicator();
    var policy = PluginManagementPolicy.getInstance();
    var installed = new ArrayList<PluginNode>();

    for (var plugin : plugins) {
      if (PluginManagerCore.getPlugin(plugin.getPluginId()) != null) {
        LOG.info("already installed: " + plugin.getPluginId());
        continue;
      }
      if (!policy.canInstallPlugin(plugin)) {
        LOG.info("rejected by policy: " + plugin.getPluginId());
        continue;
      }
      try {
        var downloader = PluginDownloader.createDownloader(plugin, plugin.getRepositoryName(), null);
        if (downloader.prepareToInstall(indicator)) {
          PluginInstaller.unpackPlugin(downloader.getFilePath(), PathManager.getPluginsDir());
          installed.add(plugin);
        }
      }
      catch (Exception e) {
        LOG.error("cannot install: " + plugin.getPluginId(), e);
      }
    }

    PluginEnabler.HEADLESS.enable(installed);

    return new ArrayList<>(installed);
  }
}
