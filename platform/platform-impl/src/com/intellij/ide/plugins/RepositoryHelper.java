/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.google.common.hash.Hashing;
import com.google.common.io.Files;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.updateSettings.impl.UpdateSettings;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.io.HttpRequests;
import com.intellij.util.io.URLUtil;
import org.apache.http.client.utils.URIBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.net.URLConnection;
import java.util.Iterator;
import java.util.List;

/**
 * @author stathik
 * @since Mar 28, 2003
 */
public class RepositoryHelper {
  private static final Logger LOG = Logger.getInstance(RepositoryHelper.class);
  @SuppressWarnings("SpellCheckingInspection") private static final String PLUGIN_LIST_FILE = "availables.xml";

  /**
   * Loads list of plugins, compatible with a current build, from a main plugin repository.
   */
  @NotNull
  public static List<IdeaPluginDescriptor> loadPlugins(@Nullable ProgressIndicator indicator) throws IOException {
    return loadPlugins(null, null, indicator);
  }

  /**
   * Loads list of plugins, compatible with a given build, from a given plugin repository (main repository if null).
   */
  @NotNull
  public static List<IdeaPluginDescriptor> loadPlugins(@Nullable String repositoryUrl,
                                                       @Nullable BuildNumber buildnumber,
                                                       @Nullable final ProgressIndicator indicator) throws IOException {
    boolean forceHttps = repositoryUrl == null && UpdateSettings.getInstance().SECURE_CONNECTION;
    return loadPlugins(repositoryUrl, buildnumber, forceHttps, indicator);
  }

  @NotNull
  public static List<IdeaPluginDescriptor> loadPlugins(@Nullable String repositoryUrl,
                                                       @Nullable BuildNumber buildnumber,
                                                       boolean forceHttps,
                                                       @Nullable final ProgressIndicator indicator) throws IOException {
    final URIBuilder uriBuilder;
    final File pluginListFile;
    try {
      if (repositoryUrl == null) {
        uriBuilder = new URIBuilder(ApplicationInfoImpl.getShadowInstance().getPluginsListUrl());
        pluginListFile = new File(PathManager.getPluginsPath(), PLUGIN_LIST_FILE);
        if (pluginListFile.length() > 0) {
          uriBuilder.addParameter("crc32", Files.hash(pluginListFile, Hashing.crc32()).toString());
        }
      }
      else {
        uriBuilder = new URIBuilder(repositoryUrl);
        pluginListFile = null;
      }
    }
    catch (URISyntaxException e) {
      throw new IOException(e);
    }
    if (!URLUtil.FILE_PROTOCOL.equals(uriBuilder.getScheme())) {
      uriBuilder.addParameter("build", (buildnumber != null ? buildnumber.asString() : ApplicationInfoImpl.getShadowInstance().getApiVersion()));
    }

    if (indicator != null) {
      indicator.setText2(IdeBundle.message("progress.connecting.to.plugin.manager", uriBuilder.getHost()));
    }

    HttpRequests.RequestBuilder request = HttpRequests.request(uriBuilder.toString()).forceHttps(forceHttps);
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
          indicator.setText2(IdeBundle.message("progress.downloading.list.of.plugins", uriBuilder.getHost()));
        }

        if (pluginListFile != null) {
          synchronized (RepositoryHelper.class) {
            FileUtil.ensureExists(pluginListFile.getParentFile());
            request.saveToFile(pluginListFile, indicator);
            return loadPluginList(pluginListFile);
          }
        }
        else {
          return parsePluginList(request.getInputStream());
        }
      }
    }));
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
    InputStream stream = new FileInputStream(file);
    try {
      return parsePluginList(stream);
    }
    finally {
      stream.close();
    }
  }

  private static List<IdeaPluginDescriptor> parsePluginList(@NotNull InputStream stream) throws IOException {
    try {
      SAXParser parser = SAXParserFactory.newInstance().newSAXParser();
      RepositoryContentHandler handler = new RepositoryContentHandler();
      parser.parse(stream, handler);
      return handler.getPluginsList();
    }
    catch (ParserConfigurationException e) {
      throw new IOException(e);
    }
    catch (SAXException e) {
      throw new IOException(e);
    }
  }

  private static List<IdeaPluginDescriptor> process(@Nullable String repositoryUrl, List<IdeaPluginDescriptor> list) {
    for (Iterator<IdeaPluginDescriptor> i = list.iterator(); i.hasNext(); ) {
      IdeaPluginDescriptor descriptor = i.next();
      if (descriptor.getPluginId() == null || descriptor.getUrl() == null) {
        LOG.warn("Malformed plugin record at " + repositoryUrl);
        i.remove();
      }
      else if (descriptor instanceof PluginNode) {
        PluginNode node = (PluginNode)descriptor;
        if (repositoryUrl != null) {
          node.setRepositoryName(repositoryUrl);
        }
        if (node.getName() == null) {
          String url = node.getUrl();
          String name = FileUtil.getNameWithoutExtension(url.substring(url.lastIndexOf('/') + 1));
          ((PluginNode)descriptor).setName(name);
        }
      }
    }

    return list;
  }
}
