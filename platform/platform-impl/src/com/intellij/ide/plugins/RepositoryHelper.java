// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.PermanentInstallationID;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.updateSettings.impl.UpdateSettings;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.Url;
import com.intellij.util.Urls;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.HttpRequests;
import com.intellij.util.io.URLUtil;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.text.VersionComparatorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static java.util.Collections.singletonMap;

/**
 * @author stathik
 */
public final class RepositoryHelper {
  private static final Logger LOG = Logger.getInstance(RepositoryHelper.class);
  @SuppressWarnings("SpellCheckingInspection") private static final String PLUGIN_LIST_FILE = "availables.xml";
  @SuppressWarnings("SpellCheckingInspection") private static final String TAG_EXT = ".etag";

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
  public static List<IdeaPluginDescriptor> loadPluginsFromAllRepositories(@Nullable ProgressIndicator indicator) {
    List<IdeaPluginDescriptor> result = new ArrayList<>();
    Set<PluginId> addedPluginIds = new HashSet<>();
    for (String host : getPluginHosts()) {
      try {
        List<IdeaPluginDescriptor> plugins = loadPlugins(host, indicator);
        for (IdeaPluginDescriptor plugin : plugins) {
          if (addedPluginIds.add(plugin.getPluginId())) {
            result.add(plugin);
          }
        }
      }
      catch (IOException e) {
        LOG.info("Couldn't load plugins from " + (host == null ? "main repository" : host) + ": " + e);
        LOG.debug(e);
      }
    }
    return result;
  }

  /**
   * Loads list of plugins, compatible with a current build, from a main plugin repository.
   */
  @NotNull
  public static List<IdeaPluginDescriptor> loadPlugins(@Nullable ProgressIndicator indicator) throws IOException {
    return loadPlugins(null, indicator);
  }

  @NotNull
  public static List<IdeaPluginDescriptor> loadPlugins(@Nullable String repositoryUrl, @Nullable ProgressIndicator indicator) throws IOException {
    return loadPlugins(repositoryUrl, null, indicator);
  }

  @NotNull
  public static List<IdeaPluginDescriptor> loadPlugins(@Nullable String repositoryUrl,
                                                       @Nullable BuildNumber build,
                                                       @Nullable ProgressIndicator indicator) throws IOException {
    String eTag;
    File pluginListFile;
    Url url;
    if (repositoryUrl == null) {
      String base = ApplicationInfoImpl.getShadowInstance().getPluginsListUrl();
      url = Urls.newFromEncoded(base).addParameters(singletonMap("uuid", PermanentInstallationID.get()));
      pluginListFile = new File(PathManager.getPluginsPath(), PLUGIN_LIST_FILE);
      eTag = loadPluginListETag(pluginListFile);
    }
    else {
      url = Urls.newFromEncoded(repositoryUrl);
      pluginListFile = null;
      eTag = "";
    }

    if (!URLUtil.FILE_PROTOCOL.equals(url.getScheme())) {
      url = url.addParameters(singletonMap("build", build != null ? build.asString() : ApplicationInfoImpl.getShadowInstance().getApiVersion()));
    }

    if (indicator != null) {
      indicator.setText2(IdeBundle.message("progress.connecting.to.plugin.manager", url.getAuthority()));
    }

    Url finalUrl = url;
    List<PluginNode> descriptors = HttpRequests.request(url)
      .tuner(connection -> connection.setRequestProperty("If-None-Match", eTag))
      .productNameAsUserAgent()
      .connect(request -> {
        if (indicator != null) {
          indicator.checkCanceled();
        }

        URLConnection connection = request.getConnection();
        if (pluginListFile != null &&
            pluginListFile.length() > 0 &&
            connection instanceof HttpURLConnection &&
            ((HttpURLConnection)connection).getResponseCode() == HttpURLConnection.HTTP_NOT_MODIFIED) {
          LOG.info("using cached plugin list (updated at " + DateFormatUtil.formatDateTime(pluginListFile.lastModified()) + ")");
          return loadPluginList(pluginListFile);
        }

        if (indicator != null) {
          indicator.checkCanceled();
          indicator.setText2(IdeBundle.message("progress.downloading.list.of.plugins", finalUrl.getAuthority()));
        }

        if (pluginListFile != null) {
          synchronized (PLUGIN_LIST_FILE) {
            FileUtil.ensureExists(pluginListFile.getParentFile());
            request.saveToFile(pluginListFile, indicator);
            savePluginListETag(pluginListFile, connection.getHeaderField("ETag"));
            return loadPluginList(pluginListFile);
          }
        }
        else {
          try (BufferedReader reader = request.getReader()) {
            return parsePluginList(reader);
          }
        }
      });

    return process(descriptors, repositoryUrl, build);
  }

  private static String loadPluginListETag(File pluginListFile) {
    File file = getPluginListETagFile(pluginListFile);
    if (file.length() > 0) {
      try {
        List<String> lines = FileUtil.loadLines(file);
        if (lines.size() != 1) {
          LOG.warn("Can't load plugin list ETag from '" + file.getAbsolutePath() + "'. Unexpected number of lines: " + lines.size());
          FileUtil.delete(file);
        }
        else {
          return lines.get(0);
        }
      }
      catch (IOException e) {
        LOG.warn("Can't load plugin list ETag from '" + file.getAbsolutePath() + "'", e);
      }
    }

    return "";
  }

  private static void savePluginListETag(File pluginListFile, String eTag) {
    if (eTag != null) {
      File file = getPluginListETagFile(pluginListFile);
      try {
        FileUtil.writeToFile(file, eTag);
      }
      catch (IOException e) {
        LOG.warn("Can't save plugin list ETag to '" + file.getAbsolutePath() + "'", e);
      }
    }
  }

  private static File getPluginListETagFile(File pluginListFile) {
    return new File(pluginListFile.getParentFile(), pluginListFile.getName() + TAG_EXT);
  }

  /**
   * Reads cached plugin descriptors from a file. Returns {@code null} if cache file does not exist.
   */
  @Nullable
  public static List<IdeaPluginDescriptor> loadCachedPlugins() throws IOException {
    File file = new File(PathManager.getPluginsPath(), PLUGIN_LIST_FILE);
    return file.length() > 0 ? process(loadPluginList(file), null, null) : null;
  }

  private static List<PluginNode> loadPluginList(File file) throws IOException {
    try (Reader reader = new InputStreamReader(new BufferedInputStream(new FileInputStream(file)), StandardCharsets.UTF_8)) {
      return parsePluginList(reader);
    }
  }

  private static List<PluginNode> parsePluginList(Reader reader) throws IOException {
    try {
      SAXParser parser = SAXParserFactory.newInstance().newSAXParser();
      RepositoryContentHandler handler = new RepositoryContentHandler();
      parser.parse(new InputSource(reader), handler);
      return handler.getPluginsList();
    }
    catch (ParserConfigurationException | SAXException | RuntimeException e) {
      throw new IOException(e);
    }
  }

  private static List<IdeaPluginDescriptor> process(List<PluginNode> list, @Nullable String repositoryUrl, @Nullable BuildNumber build) {
    Map<PluginId, IdeaPluginDescriptor> result = new LinkedHashMap<>(list.size());
    if (build == null) build = PluginManagerCore.getBuildNumber();

    for (PluginNode node : list) {
      PluginId pluginId = node.getPluginId();

      if (pluginId == null || repositoryUrl != null && node.getDownloadUrl() == null) {
        LOG.debug("Malformed plugin record (id:" + pluginId + " repository:" + repositoryUrl + ")");
        continue;
      }
      if (PluginManagerCore.isBrokenPlugin(node) || PluginManagerCore.isIncompatible(node, build)) {
        LOG.debug("Incompatible plugin (id:" + pluginId + " repository:" + repositoryUrl + ")");
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
    }

    return new ArrayList<>(result.values());
  }
}