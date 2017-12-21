/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.openapi.application.PermanentInstallationID;
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
import com.intellij.util.io.URLUtil;
import com.intellij.util.text.DateFormatUtil;
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
  @SuppressWarnings("SpellCheckingInspection") private static final String TAG_EXT = ".etag";

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
    return loadPlugins(repositoryUrl, buildnumber, forceHttps, indicator);
  }

  @NotNull
  public static List<IdeaPluginDescriptor> loadPlugins(@Nullable String repositoryUrl,
                                                       @Nullable BuildNumber build,
                                                       boolean forceHttps,
                                                       @Nullable ProgressIndicator indicator) throws IOException {
    String url, host, eTag;
    File pluginListFile;

    try {
      URIBuilder uriBuilder;

      if (repositoryUrl == null) {
        uriBuilder = new URIBuilder(ApplicationInfoImpl.getShadowInstance().getPluginsListUrl());
        uriBuilder.addParameter("uuid", PermanentInstallationID.get());
        pluginListFile = new File(PathManager.getPluginsPath(), PLUGIN_LIST_FILE);
        eTag = loadPluginListETag(pluginListFile);
      }
      else {
        uriBuilder = new URIBuilder(repositoryUrl);
        pluginListFile = null;
        eTag = "";
      }

      if (!URLUtil.FILE_PROTOCOL.equals(uriBuilder.getScheme())) {
        uriBuilder.addParameter("build", build != null ? build.asString() : ApplicationInfoImpl.getShadowInstance().getApiVersion());
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

    List<IdeaPluginDescriptor> descriptors = HttpRequests.request(url)
      .forceHttps(forceHttps)
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
          indicator.setText2(IdeBundle.message("progress.downloading.list.of.plugins", host));
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
          return parsePluginList(request.getReader());
        }
      });

    return process(repositoryUrl, descriptors);
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
   * Reads cached plugin descriptors from a file. Returns null if cache file does not exist.
   */
  @Nullable
  public static List<IdeaPluginDescriptor> loadCachedPlugins() throws IOException {
    File file = new File(PathManager.getPluginsPath(), PLUGIN_LIST_FILE);
    return file.length() > 0 ? loadPluginList(file) : null;
  }

  private static List<IdeaPluginDescriptor> loadPluginList(File file) throws IOException {
    return parsePluginList(new InputStreamReader(new BufferedInputStream(new FileInputStream(file)), CharsetToolkit.UTF8_CHARSET));
  }

  private static List<IdeaPluginDescriptor> parsePluginList(Reader reader) throws IOException {
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

  private static List<IdeaPluginDescriptor> process(String repositoryUrl, List<IdeaPluginDescriptor> list) {
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