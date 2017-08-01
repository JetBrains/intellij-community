/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.plugins;

import com.intellij.ide.IdeBundle;
import com.intellij.idea.IdeaApplication;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.updateSettings.impl.UpdateSettings;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.HttpRequests;
import com.intellij.util.io.RequestBuilder;
import com.intellij.util.io.URLUtil;
import org.apache.http.client.utils.URIBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.net.URLConnection;
import java.util.*;

/**
 * @author stathik
 * @since Mar 28, 2003
 */
public class RepositoryHelper {
  private static final Logger LOG = Logger.getInstance(RepositoryHelper.class);
  @SuppressWarnings("SpellCheckingInspection") private static final String PLUGIN_LIST_FILE = "availables.xml";

  /**
   * Returns a list of configured plugin hosts.
   * Note that the list always ends with {@code null} element denoting a main plugin repository.
   */
  @NotNull
  public static List<String> getPluginHosts() {
    List<String> hosts = ContainerUtil.newArrayList(UpdateSettings.getInstance().getPluginHosts());
    ContainerUtil.addIfNotNull(hosts, ApplicationInfoEx.getInstanceEx().getBuiltinPluginsUrl());
    hosts.add(null);  // main plugin repository
    return hosts;
  }

  /**
   * Loads list of plugins, compatible with a current build, from all configured repositories
   */
  @NotNull
  public static List<IdeaPluginDescriptor> loadPluginsFromAllRepositories(@Nullable ProgressIndicator indicator) throws IOException {
    List<IdeaPluginDescriptor> result = new ArrayList<>();
    Set<String> addedPluginIds = new HashSet<>();
    for (String host : getPluginHosts()) {
      List<IdeaPluginDescriptor> plugins = loadPlugins(host, indicator);
      for (IdeaPluginDescriptor plugin : plugins) {
        if (addedPluginIds.add(plugin.getPluginId().getIdString())) {
          result.add(plugin);
        }
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
                                                       @Nullable BuildNumber buildnumber,
                                                       @Nullable ProgressIndicator indicator) throws IOException {
    boolean forceHttps = repositoryUrl == null && IdeaApplication.isLoaded() && UpdateSettings.getInstance().canUseSecureConnection();
    return loadPlugins(repositoryUrl, buildnumber, null, forceHttps, indicator);
  }

  @NotNull
  public static List<IdeaPluginDescriptor> loadPlugins(@Nullable String repositoryUrl,
                                                       @Nullable BuildNumber buildnumber,
                                                       @Nullable String channel,
                                                       boolean forceHttps,
                                                       @Nullable final ProgressIndicator indicator) throws IOException {
    String url;
    final File pluginListFile;
    final String eTag;
    final String host;

    try {
      URIBuilder uriBuilder;
      if (repositoryUrl == null) {
        uriBuilder = new URIBuilder(ApplicationInfoImpl.getShadowInstance().getPluginsListUrl());
        pluginListFile = new File(PathManager.getPluginsPath(), channel == null ? PLUGIN_LIST_FILE : channel + "_" + PLUGIN_LIST_FILE);
        eTag = pluginListFile.length() > 0 ? loadPluginListETag(pluginListFile) : "";
      }
      else {
        uriBuilder = new URIBuilder(repositoryUrl);
        pluginListFile = null;
        eTag = "";
      }

      if (!URLUtil.FILE_PROTOCOL.equals(uriBuilder.getScheme())) {
        uriBuilder.addParameter("build",
                                (buildnumber != null ? buildnumber.asString() : ApplicationInfoImpl.getShadowInstance().getApiVersion()));
        if (channel != null) uriBuilder.addParameter("channel", channel);
      }

      host = uriBuilder.getHost();
      url = uriBuilder.build().toString();
    }
    catch (URISyntaxException e) {
      throw new IOException(e);
    }

    if (indicator != null) {
      indicator.setText2(IdeBundle.message("progress.connecting.to.plugin.manager", host));
    }

    RequestBuilder request = HttpRequests.request(url).forceHttps(forceHttps).tuner(connection -> connection.setRequestProperty("If-None-Match", eTag)).productNameAsUserAgent();
    return process(repositoryUrl, request.connect(new HttpRequests.RequestProcessor<List<IdeaPluginDescriptor>>() {
      @Override
      public List<IdeaPluginDescriptor> process(@NotNull HttpRequests.Request request) throws IOException {
        if (indicator != null) {
          indicator.checkCanceled();
        }

        URLConnection connection = request.getConnection();
        if (pluginListFile != null &&
            pluginListFile.length() > 0 &&
            connection instanceof HttpURLConnection &&
            ((HttpURLConnection)connection).getResponseCode() == HttpURLConnection.HTTP_NOT_MODIFIED) {
          return loadPluginList(pluginListFile);
        }

        if (indicator != null) {
          indicator.checkCanceled();
          indicator.setText2(IdeBundle.message("progress.downloading.list.of.plugins", host));
        }

        if (pluginListFile != null) {
          synchronized (PLUGIN_LIST_FILE) {
            FileUtil.ensureExists(pluginListFile.getParentFile());
            request.saveToFile(pluginListFile, indicator);
            savePluginListETag(pluginListFile, connection);
            return loadPluginList(pluginListFile);
          }
        }
        else {
          return parsePluginList(request.getReader());
        }
      }
    }));
  }

  @NotNull
  private static String loadPluginListETag(@NotNull File pluginListFile) {
    String eTag = "";
    File pluginListETagFile = getPluginListETagFile(pluginListFile);
    try {
      List<String> lines = FileUtil.loadLines(pluginListETagFile);
      if (lines.size() != 1) {
        LOG.warn("Couldn't load plugin list ETag from '" + pluginListETagFile.getAbsolutePath() + "'. Unexpected number of lines: " + lines.size());
        FileUtil.delete(pluginListETagFile);
      } else {
        eTag = lines.get(0);
      }
    }
    catch (Exception e) {
      LOG.warn("Couldn't load plugin list ETag from '" + pluginListETagFile.getAbsolutePath() + "'", e);
    }
    return eTag;
  }

  private static void savePluginListETag(@NotNull File pluginListFile, @NotNull URLConnection connection) {
    File pluginListETagFile = getPluginListETagFile(pluginListFile);
    String eTag = connection.getHeaderField("ETag");
    if (eTag != null) {
      try {
        FileUtil.writeToFile(pluginListETagFile, eTag);
      }
      catch (Exception e) {
        LOG.warn("Couldn't save plugin list ETag to '" + pluginListETagFile.getAbsolutePath() + "'", e);
      }
    }
  }

  @NotNull
  private static File getPluginListETagFile(@NotNull File pluginListFile) {
    return new File(pluginListFile.getParentFile(), pluginListFile.getName() + ".etag");
  }

  /**
   * Reads cached plugin descriptors from a file. Returns null if cache file does not exist.
   */
  @Nullable
  public static List<IdeaPluginDescriptor> loadCachedPlugins() throws IOException {
    File file = new File(PathManager.getPluginsPath(), PLUGIN_LIST_FILE);
    return file.length() == 0 ? null : loadPluginList(file);
  }

  private static List<IdeaPluginDescriptor> loadPluginList(@NotNull File file) throws IOException {
    return parsePluginList(new InputStreamReader(new BufferedInputStream(new FileInputStream(file)), CharsetToolkit.UTF8_CHARSET));
  }

  private static List<IdeaPluginDescriptor> parsePluginList(@NotNull Reader reader) throws IOException {
    try {
      SAXParser parser = SAXParserFactory.newInstance().newSAXParser();
      RepositoryContentHandler handler = new RepositoryContentHandler();
      parser.parse(new InputSource(reader), handler);
      return handler.getPluginsList();
    }
    catch (ParserConfigurationException | SAXException | RuntimeException e) {
      throw new IOException(e);
    }
    finally {
      reader.close();
    }
  }

  private static List<IdeaPluginDescriptor> process(@Nullable String repositoryUrl, List<IdeaPluginDescriptor> list) {
    for (Iterator<IdeaPluginDescriptor> i = list.iterator(); i.hasNext(); ) {
      PluginNode node = (PluginNode)i.next();

      if (node.getPluginId() == null || repositoryUrl != null && node.getDownloadUrl() == null) {
        LOG.warn("Malformed plugin record (id:" + node.getPluginId() + " repository:" + repositoryUrl + ")");
        i.remove();
        continue;
      }

      if (repositoryUrl != null) {
        node.setRepositoryName(repositoryUrl);
      }

      if (node.getName() == null) {
        String url = node.getDownloadUrl();
        String name = FileUtil.getNameWithoutExtension(url.substring(url.lastIndexOf('/') + 1));
        node.setName(name);
      }
    }

    return list;
  }
}