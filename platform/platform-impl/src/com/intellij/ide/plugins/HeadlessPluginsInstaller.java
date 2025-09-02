// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins;

import com.intellij.externalDependencies.DependencyOnPlugin;
import com.intellij.externalDependencies.ExternalDependenciesManager;
import com.intellij.ide.CommandLineProcessorKt;
import com.intellij.ide.impl.OpenProjectTaskKt;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.application.ApplicationStarter;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.updateSettings.impl.PluginDownloader;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.URLUtil;
import kotlin.Unit;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.*;

@SuppressWarnings("UseOfSystemOutOrSystemErr")
public class HeadlessPluginsInstaller implements ApplicationStarter {
  private static final Logger LOG = Logger.getInstance(HeadlessPluginsInstaller.class);

  @Override
  public int getRequiredModality() {
    //noinspection MagicConstant (workaround for IDEA-359236)
    return NOT_IN_EDT;
  }

  @Override
  public void main(@NotNull List<String> args) {
    if (args.size() == 1) {
      printUsageHint();
      System.exit(0);
    }

    try {
      var pluginIds = new LinkedHashSet<PluginId>();
      var customRepositories = new LinkedHashSet<String>();
      var projectPaths = new ArrayList<Path>();
      var giveConsentToUseThirdPartyPlugins = false;

      for (int i = 1; i < args.size(); i++) {
        var arg = args.get(i);
        if ("-h".equals(arg) || "--help".equals(arg)) {
          printUsageHint();
        }
        else if ("--give-consent-to-use-third-party-plugins".equals(arg)) {
          giveConsentToUseThirdPartyPlugins = true;
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
        RepositoryHelper.amendPluginHostsProperty(customRepositories);
      }
      logInfo("plugin repositories: " + RepositoryHelper.getPluginHosts());

      var installed = installPlugins(pluginIds, giveConsentToUseThirdPartyPlugins);
      System.exit(installed.size() == pluginIds.size() ? 0 : 1);
    }
    catch (Throwable t) {
      LOG.error(t);
      System.exit(1);
    }
  }

  private void printUsageHint() {
    var commandName = CommandLineProcessorKt.getCommandNameFromExtension(this);
    System.out.printf(
      """
        Usage: %s pluginId* repository* (--for-project=<project-path>)* [--give-consent-to-use-third-party-plugins]

        Installs plugins with `pluginId` from the Marketplace or provided `repository`-es.
        If `--for-project` is specified, also installs the required plugins for a project located at <project-path>.
        If `--give-consent-to-use-third-party-plugins` is specified, installed third-party plugins will be approved automatically.
        Without this option, if a third-party plugin is installed, a user will be asked to approve it when the IDE starts.%n""",
      commandName);
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
    return installPlugins(pluginIds, false);
  }
    
  private static @NotNull Collection<PluginNode> installPlugins(@NotNull Set<PluginId> pluginIds, boolean giveConsentToUseThirdPartyPlugins) {
    logInfo("looking up plugins: " + pluginIds);
    var plugins = RepositoryHelper.loadPlugins(pluginIds);

    if (!giveConsentToUseThirdPartyPlugins && !PluginManagerMain.checkThirdPartyPluginsAllowed(plugins)) {
      logInfo("3rd-party plugins rejected");
      return Collections.emptyList();
    }

    if (plugins.size() < pluginIds.size()) {
      var unknown = new HashSet<>(pluginIds);
      for (var plugin : plugins) unknown.remove(plugin.getPluginId());
      logInfo("unknown plugins: " + unknown);
    }

    @SuppressWarnings("UsagesOfObsoleteApi") var indicator = new EmptyProgressIndicator();
    var policy = PluginManagementPolicy.getInstance();
    var installed = new ArrayList<PluginNode>();

    for (var plugin : plugins) {
      if (PluginManagerCore.getPlugin(plugin.getPluginId()) != null) {
        logInfo("already installed: " + plugin.getPluginId());
        installed.add(plugin);
        continue;
      }
      if (!policy.canInstallPlugin(plugin)) {
        logInfo("rejected by policy: " + plugin.getPluginId());
        continue;
      }
      try {
        var downloader = PluginDownloader.createDownloader(plugin, plugin.getRepositoryName(), null);
        if (downloader.prepareToInstall(indicator)) {
          PluginInstaller.unpackPlugin(downloader.getFilePath(), PathManager.getPluginsDir());
          installed.add(plugin);
          logInfo("installed plugin: " + plugin);
        }
      }
      catch (Exception e) {
        LOG.error("cannot install: " + plugin.getPluginId(), e);
      }
    }

    if (giveConsentToUseThirdPartyPlugins && 
        ContainerUtil.exists(installed, plugin -> !plugin.isBundled() && !PluginManagerCore.isVendorTrusted(plugin))) {
      ThirdPartyPluginsWithoutConsentFile.giveConsentToSpecificThirdPartyPlugins(pluginIds);
    }
    PluginEnabler.HEADLESS.enable(installed);

    return new ArrayList<>(installed);
  }

  private static void logInfo(String message) {
    // info level logs are not printed to stdout/stderr by default, and toolbox does not include stdout/stderr in its log
    System.out.println(message);
    LOG.info(message);
  }
}
