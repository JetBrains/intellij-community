// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.marketplace.MarketplaceRequests;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.updateSettings.impl.PluginDownloader;
import com.intellij.openapi.updateSettings.impl.UpdateSettings;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.Url;
import com.intellij.util.Urls;
import com.intellij.util.io.URLUtil;
import com.intellij.util.text.VersionComparatorUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author stathik
 */
public final class RepositoryHelper {
  private static final Logger LOG = Logger.getInstance(RepositoryHelper.class);

  @SuppressWarnings("SpellCheckingInspection") private static final String PLUGIN_LIST_FILE = "availables.xml";
  private static final String MARKETPLACE_PLUGIN_ID = "com.intellij.marketplace";
  private static final String ULTIMATE_MODULE = "com.intellij.modules.ultimate";

  /**
   * Returns a list of configured plugin hosts.
   * Note that the list always ends with {@code null} element denoting a main plugin repository.
   */
  public static @NotNull List<String> getPluginHosts() {
    List<String> hosts = new ArrayList<>(UpdateSettings.getInstance().getPluginHosts());
    String pluginsUrl = ApplicationInfoEx.getInstanceEx().getBuiltinPluginsUrl();
    if (pluginsUrl != null && !"__BUILTIN_PLUGINS_URL__".equals(pluginsUrl)) {
      hosts.add(pluginsUrl);
    }
    List<CustomPluginRepoContributor> repoContributors = CustomPluginRepoContributor.EP_NAME.getExtensionsIfPointIsRegistered();
    for (CustomPluginRepoContributor contributor : repoContributors) {
      hosts.addAll(contributor.getRepoUrls());
    }
    hosts.add(null);  // main plugin repository
    return hosts;
  }

  /**
   * Use method only for getting plugins from custom repositories
   *
   * @deprecated Please use {@link #loadPlugins(String, BuildNumber, ProgressIndicator)} to get a list of {@link PluginNode}s.
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  public static @NotNull List<IdeaPluginDescriptor> loadPlugins(@Nullable String repositoryUrl,
                                                                @Nullable ProgressIndicator indicator) throws IOException {
    return new ArrayList<>(loadPlugins(repositoryUrl, null, indicator));
  }

  /**
   * Use method only for getting plugins from custom repositories
   */
  public static @NotNull List<PluginNode> loadPlugins(@Nullable String repositoryUrl,
                                                      @Nullable BuildNumber build,
                                                      @Nullable ProgressIndicator indicator) throws IOException {
    Path pluginListFile;
    Url url;
    if (repositoryUrl == null) {
      if (ApplicationInfoImpl.getShadowInstance().usesJetBrainsPluginRepository()) {
        LOG.error("Using deprecated API for getting plugins from Marketplace");
      }
      String base = ApplicationInfoImpl.getShadowInstance().getPluginsListUrl();
      url = Urls.newFromEncoded(base).addParameters(Map.of("uuid", PluginDownloader.getMarketplaceDownloadsUUID()));  // NON-NLS
      pluginListFile = Paths.get(PathManager.getPluginsPath(), PLUGIN_LIST_FILE);
    }
    else {
      url = Urls.newFromEncoded(repositoryUrl);
      pluginListFile = null;
    }

    if (!URLUtil.FILE_PROTOCOL.equals(url.getScheme())) {
      url = url.addParameters(Map.of("build", ApplicationInfoImpl.orFromPluginsCompatibleBuild(build)));
    }

    if (indicator != null) {
      indicator.setText2(IdeBundle.message("progress.connecting.to.plugin.manager", url.getAuthority()));
    }

    List<PluginNode> descriptors = MarketplaceRequests.readOrUpdateFile(pluginListFile,
                                                                        url.toExternalForm(),
                                                                        indicator,
                                                                        IdeBundle.message("progress.downloading.list.of.plugins",
                                                                                          url.getAuthority()),
                                                                        MarketplaceRequests::parsePluginList);
    return process(descriptors,
                   build != null ? build : PluginManagerCore.getBuildNumber(),
                   repositoryUrl);
  }

  private static @NotNull List<PluginNode> process(@NotNull List<PluginNode> list,
                                                   @NotNull BuildNumber build,
                                                   @Nullable String repositoryUrl) {
    Map<PluginId, PluginNode> result = new LinkedHashMap<>(list.size());

    boolean isPaidPluginsRequireMarketplacePlugin = isPaidPluginsRequireMarketplacePlugin();

    for (PluginNode node : list) {
      PluginId pluginId = node.getPluginId();

      if (repositoryUrl != null && node.getDownloadUrl() == null) {
        LOG.debug("Malformed plugin record (id:" + pluginId + " repository:" + repositoryUrl + ")");
        continue;
      }

      if (PluginManagerCore.isBrokenPlugin(node) || PluginManagerCore.isIncompatible(node, build)) {
        LOG.debug("An incompatible plugin (id:" + pluginId + " repository:" + repositoryUrl + ")");
        continue;
      }

      if (repositoryUrl != null) {
        node.setRepositoryName(repositoryUrl);
      }
      if (node.getName() == null) {
        String url = node.getDownloadUrl();
        node.setName(FileUtilRt.getNameWithoutExtension(url.substring(url.lastIndexOf('/') + 1)));
      }

      PluginNode previous = result.get(pluginId);
      if (previous == null || VersionComparatorUtil.compare(node.getVersion(), previous.getVersion()) > 0) {
        result.put(pluginId, node);
      }

      addMarketplacePluginDependencyIfRequired(node, isPaidPluginsRequireMarketplacePlugin);
    }

    return result
      .values()
      .stream()
      .collect(Collectors.toUnmodifiableList());
  }

  /**
   * If plugin is paid (has `productCode`) and IDE is not JetBrains "ultimate" then MARKETPLACE_PLUGIN_ID is required
   */
  public static void addMarketplacePluginDependencyIfRequired(@NotNull PluginNode node) {
    boolean isPaidPluginsRequireMarketplacePlugin = isPaidPluginsRequireMarketplacePlugin();
    addMarketplacePluginDependencyIfRequired(node, isPaidPluginsRequireMarketplacePlugin);
  }

  private static boolean isPaidPluginsRequireMarketplacePlugin() {
    boolean isCommunityIDE = !ideContainsUltimateModule();
    boolean isVendorNotJetBrains = !ApplicationInfoImpl.getShadowInstance().isVendorJetBrains();
    return isCommunityIDE || isVendorNotJetBrains;
  }

  private static void addMarketplacePluginDependencyIfRequired(@NotNull PluginNode node, boolean isPaidPluginsRequireMarketplacePlugin) {
    if (isPaidPluginsRequireMarketplacePlugin && node.getProductCode() != null) {
      node.addDepends(MARKETPLACE_PLUGIN_ID, false);
    }
  }

  private static boolean ideContainsUltimateModule() {
    IdeaPluginDescriptorImpl corePlugin = PluginManagerCore.findPlugin(PluginManagerCore.CORE_ID);
    return corePlugin != null && corePlugin.modules.contains(PluginId.getId(ULTIMATE_MODULE));
  }

  @ApiStatus.Internal
  public static @NotNull Collection<PluginNode> mergePluginsFromRepositories(@NotNull List<PluginNode> marketplacePlugins,
                                                                             @NotNull List<PluginNode> customPlugins,
                                                                             boolean addMissing) {
    Map<PluginId, PluginNode> compatiblePluginMap = new LinkedHashMap<>(marketplacePlugins.size());

    for (PluginNode marketplacePlugin : marketplacePlugins) {
      compatiblePluginMap.put(marketplacePlugin.getPluginId(), marketplacePlugin);
    }

    for (PluginNode customPlugin : customPlugins) {
      PluginId pluginId = customPlugin.getPluginId();
      IdeaPluginDescriptor plugin = compatiblePluginMap.get(pluginId);
      if (plugin == null && addMissing ||
          plugin != null && PluginDownloader.compareVersionsSkipBrokenAndIncompatible(customPlugin.getVersion(), plugin) > 0) {
        compatiblePluginMap.put(pluginId, customPlugin);
      }
    }

    return compatiblePluginMap.values();
  }
}
