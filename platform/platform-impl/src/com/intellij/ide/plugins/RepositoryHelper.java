// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.marketplace.MarketplaceRequests;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.PermanentInstallationID;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.updateSettings.impl.UpdateSettings;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.Url;
import com.intellij.util.Urls;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.URLUtil;
import com.intellij.util.text.VersionComparatorUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static com.intellij.ide.plugins.marketplace.MarketplaceRequests.parsePluginList;
import static java.util.Collections.singletonMap;

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
  @NotNull
  public static List<String> getPluginHosts() {
    List<String> hosts = new ArrayList<>(UpdateSettings.getInstance().getPluginHosts());
    ContainerUtil.addIfNotNull(hosts, ApplicationInfoEx.getInstanceEx().getBuiltinPluginsUrl());
    hosts.add(null);  // main plugin repository
    return hosts;
  }

  /**
   * Loads list of plugins, compatible with a current build, from all configured repositories
   */
  @NotNull
  public static List<IdeaPluginDescriptor> loadPluginsFromCustomRepositories(@Nullable ProgressIndicator indicator) {
    List<IdeaPluginDescriptor> result = new ArrayList<>();
    Set<PluginId> addedPluginIds = new HashSet<>();
    for (String host : getPluginHosts()) {
      if (host == null && ApplicationInfoEx.getInstanceEx().usesJetBrainsPluginRepository()) continue;
      try {
        List<IdeaPluginDescriptor> plugins = loadPlugins(host, indicator);
        for (IdeaPluginDescriptor plugin : plugins) {
          if (addedPluginIds.add(plugin.getPluginId())) {
            result.add(plugin);
          }
        }
      }
      catch (IOException e) {
        LOG.info("Couldn't load plugins from" + host + ":" + e);
        LOG.debug(e);
      }
    }
    return result;
  }

  /**
   * Loads list of plugins, compatible with a current build, from a main plugin repository.
   * @deprecated Use `loadPlugins` only for custom repositories. Use {@link MarketplaceRequests} for getting descriptors.
   */
  @NotNull
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.2")
  public static List<IdeaPluginDescriptor> loadPlugins(@Nullable ProgressIndicator indicator) throws IOException {
    return loadPlugins(null, indicator);
  }

  /**
   * Use method only for getting plugins from custom repositories
   */
  @NotNull
  public static List<IdeaPluginDescriptor> loadPlugins(@Nullable String repositoryUrl, @Nullable ProgressIndicator indicator) throws IOException {
    return loadPlugins(repositoryUrl, null, indicator);
  }

  /**
   * Use method only for getting plugins from custom repositories
   */
  @NotNull
  public static List<IdeaPluginDescriptor> loadPlugins(@Nullable String repositoryUrl,
                                                       @Nullable BuildNumber build,
                                                       @Nullable ProgressIndicator indicator) throws IOException {
    File pluginListFile;
    Url url;
    if (repositoryUrl == null) {
      LOG.error("Using deprecated API for getting plugins from Marketplace");
      String base = ApplicationInfoImpl.getShadowInstance().getPluginsListUrl();
      url = Urls.newFromEncoded(base).addParameters(singletonMap("uuid", PermanentInstallationID.get()));
      pluginListFile = new File(PathManager.getPluginsPath(), PLUGIN_LIST_FILE);
    }
    else {
      url = Urls.newFromEncoded(repositoryUrl);
      pluginListFile = null;
    }

    if (!URLUtil.FILE_PROTOCOL.equals(url.getScheme())) {
      url = url.addParameters(singletonMap("build", build != null ? build.asString() : MarketplaceRequests.getInstance().getBuildForPluginRepositoryRequests()));
    }

    if (indicator != null) {
      indicator.setText2(IdeBundle.message("progress.connecting.to.plugin.manager", url.getAuthority()));
    }

    List<PluginNode> descriptors = MarketplaceRequests.getInstance().readOrUpdateFile(
      pluginListFile,
      url.toExternalForm(),
      indicator,
      IdeBundle.message("progress.downloading.list.of.plugins", url.getAuthority()),
      reader -> parsePluginList(reader)
    );
    return process(descriptors, repositoryUrl, build);
  }

  /**
   * Reads cached plugin descriptors from a file. Returns {@code null} if cache file does not exist.
   * @deprecated use `MarketplaceRequest.getMarketplaceCachedPlugins`
   */
  @Nullable
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.2")
  public static List<IdeaPluginDescriptor> loadCachedPlugins() throws IOException {
    File file = new File(PathManager.getPluginsPath(), PLUGIN_LIST_FILE);
    return file.length() > 0 ? process(loadPluginList(file), null, null) : null;
  }

  private static List<PluginNode> loadPluginList(File file) throws IOException {
    try (Reader reader = new InputStreamReader(new BufferedInputStream(new FileInputStream(file)), StandardCharsets.UTF_8)) {
      return parsePluginList(reader);
    }
  }

  private static @NotNull List<IdeaPluginDescriptor> process(@NotNull List<PluginNode> list, @Nullable String repositoryUrl, @Nullable BuildNumber build) {
    Map<PluginId, IdeaPluginDescriptor> result = new LinkedHashMap<>(list.size());
    if (build == null) {
      build = PluginManagerCore.getBuildNumber();
    }

    boolean isPaidPluginsRequireMarketplacePlugin = isPaidPluginsRequireMarketplacePlugin();

    for (PluginNode node : list) {
      PluginId pluginId = node.getPluginId();

      if (pluginId == null || repositoryUrl != null && node.getDownloadUrl() == null) {
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

      IdeaPluginDescriptor previous = result.get(pluginId);
      if (previous == null || VersionComparatorUtil.compare(node.getVersion(), previous.getVersion()) > 0) {
        result.put(pluginId, node);
      }

      addMarketplacePluginDependencyIfRequired(node, isPaidPluginsRequireMarketplacePlugin);
    }

    return new ArrayList<>(result.values());
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
    IdeaPluginDescriptor corePlugin = PluginManagerCore.getPlugin(PluginId.getId(PluginManagerCore.CORE_PLUGIN_ID));
    IdeaPluginDescriptorImpl corePluginImpl = (corePlugin instanceof IdeaPluginDescriptorImpl) ? (IdeaPluginDescriptorImpl)corePlugin : null;
    return corePluginImpl != null && corePluginImpl.getModules().contains(PluginId.getId(ULTIMATE_MODULE));
  }

}