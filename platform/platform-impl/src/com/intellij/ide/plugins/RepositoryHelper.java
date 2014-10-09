/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.PathUtil;
import com.intellij.util.net.HttpConfigurable;
import com.intellij.util.net.NetUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * @author stathik
 * @since Mar 28, 2003
 */
public class RepositoryHelper {
  @NonNls public static final String PLUGIN_LIST_FILE = "availables.xml";

  public static List<IdeaPluginDescriptor> loadPluginsFromRepository(@Nullable ProgressIndicator indicator) throws Exception {
    return loadPluginsFromRepository(indicator, null);
  }

  @NotNull
  public static Pair<URLConnection, String> openConnection(@NotNull String initialUrl, boolean supportGzip) throws IOException {
    int i = 0;
    String url = initialUrl;
    while (i++ < 99) {
      URLConnection connection;
      if (ApplicationManager.getApplication() != null) {
        connection = HttpConfigurable.getInstance().openConnection(url);
      }
      else {
        connection = new URL(url).openConnection();
        connection.setConnectTimeout(HttpConfigurable.CONNECTION_TIMEOUT);
        connection.setReadTimeout(HttpConfigurable.CONNECTION_TIMEOUT);
      }

      if (supportGzip) {
        connection.setRequestProperty("Accept-Encoding", "gzip");
      }
      connection.setUseCaches(false);

      if (connection instanceof HttpURLConnection) {
        int responseCode = ((HttpURLConnection)connection).getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK && responseCode != HttpURLConnection.HTTP_NOT_MODIFIED) {
          if (responseCode == HttpURLConnection.HTTP_MOVED_PERM || responseCode == HttpURLConnection.HTTP_MOVED_TEMP) {
            url = connection.getHeaderField("Location");
          }
          else {
            url = null;
          }

          if (url == null) {
            throw new IOException(IdeBundle.message("error.connection.failed.with.http.code.N", responseCode));
          }
          else {
            ((HttpURLConnection)connection).disconnect();
            continue;
          }
        }
      }
      return Pair.create(connection, url == initialUrl ? null : url);
    }
    throw new IOException("Infinite redirection");
  }

  @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
  @NotNull
  public static InputStream getConnectionInputStream(@NotNull URLConnection connection) throws IOException {
    InputStream inputStream = connection.getInputStream();
    if ("gzip".equalsIgnoreCase(connection.getContentEncoding())) {
      try {
        return new GZIPInputStream(inputStream);
      }
      catch (IOException e) {
        inputStream.close();
        throw e;
      }
    }
    else {
      return inputStream;
    }
  }

  @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
  public static List<IdeaPluginDescriptor> loadPluginsFromRepository(@Nullable ProgressIndicator indicator, @Nullable BuildNumber buildnumber) throws Exception {
    ApplicationInfoEx appInfo = ApplicationInfoImpl.getShadowInstance();

    String url = appInfo.getPluginsListUrl() + "?build=" + (buildnumber != null ? buildnumber.asString() : appInfo.getApiVersion());

    if (indicator != null) {
      indicator.setText2(IdeBundle.message("progress.connecting.to.plugin.manager", appInfo.getPluginManagerUrl()));
    }

    File pluginListFile = new File(PathManager.getPluginsPath(), PLUGIN_LIST_FILE);
    if (pluginListFile.length() > 0) {
      try {
        url = url + "&crc32=" + Files.hash(pluginListFile, Hashing.crc32()).toString();
      }
      catch (NoSuchMethodError e) {
        String guavaPath = PathUtil.getJarPathForClass(Hashing.class);
        throw new RuntimeException(guavaPath, e);
      }
    }

    HttpURLConnection connection = (HttpURLConnection)openConnection(url, true).first;
    if (indicator != null) {
      indicator.setText2(IdeBundle.message("progress.waiting.for.reply.from.plugin.manager", appInfo.getPluginManagerUrl()));
    }
    connection.connect();
    try {
      if (indicator != null) {
        indicator.checkCanceled();
      }

      if (connection.getResponseCode() == HttpURLConnection.HTTP_NOT_MODIFIED) {
        return loadPluginList(pluginListFile);
      }

      if (indicator != null) {
        indicator.setText2(IdeBundle.message("progress.downloading.list.of.plugins"));
      }
      return readPluginsStream(connection, indicator, PLUGIN_LIST_FILE);
    }
    finally {
      connection.disconnect();
    }
  }

  private synchronized static List<IdeaPluginDescriptor> readPluginsStream(@NotNull URLConnection connection,
                                                                           ProgressIndicator indicator,
                                                                           @NotNull String file) throws Exception {
    File localFile;
    InputStream input = getConnectionInputStream(connection);
    try {
      localFile = createLocalPluginsDescriptions(file);
      OutputStream output = new FileOutputStream(localFile);
      try {
        NetUtils.copyStreamContent(indicator, input, output, connection.getContentLength());
      }
      finally {
        output.close();
      }
    }
    finally {
      input.close();
    }
    return loadPluginList(localFile);
  }

  private static List<IdeaPluginDescriptor> loadPluginList(File file) throws Exception {
    SAXParser parser = SAXParserFactory.newInstance().newSAXParser();
    RepositoryContentHandler handler = new RepositoryContentHandler();
    parser.parse(file, handler);
    return handler.getPluginsList();
  }

  @NotNull
  private static File createLocalPluginsDescriptions(@NotNull String file) throws IOException {
    File basePath = new File(PathManager.getPluginsPath());
    if (!basePath.isDirectory() && !basePath.mkdirs()) {
      throw new IOException("Cannot create directory: " + basePath);
    }

    File temp = new File(basePath, file);
    if (temp.exists()) {
      FileUtil.delete(temp);
    }
    else {
      FileUtilRt.createParentDirs(temp);
    }
    return temp;
  }

  public static List<IdeaPluginDescriptor> loadPluginsFromDescription(@NotNull URLConnection connection, @Nullable ProgressIndicator indicator) throws Exception {
    return readPluginsStream(connection, indicator, "host.xml");
  }

  public static String getDownloadUrl() {
    return ApplicationInfoImpl.getShadowInstance().getPluginsDownloadUrl() + "?action=download&id=";
  }
}
