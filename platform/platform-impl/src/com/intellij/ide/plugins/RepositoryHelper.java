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
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.PathUtil;
import com.intellij.util.net.HttpConfigurable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.*;
import java.net.HttpURLConnection;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * @author stathik
 * @since Mar 28, 2003
 */
public class RepositoryHelper {
  @NonNls public static final String PLUGIN_LIST_FILE = "availables.xml";

  public static List<IdeaPluginDescriptor> loadPluginsFromRepository(@Nullable ProgressIndicator indicator) throws Exception {
    ApplicationInfoEx appInfo = ApplicationInfoImpl.getShadowInstance();

    String url = appInfo.getPluginsListUrl() + "?build=" + appInfo.getApiVersion();

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

    HttpURLConnection connection = HttpConfigurable.getInstance().openHttpConnection(url);
    connection.setRequestProperty("Accept-Encoding", "gzip");

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

      String encoding = connection.getContentEncoding();
      InputStream is = connection.getInputStream();
      try {
        if ("gzip".equalsIgnoreCase(encoding)) {
          is = new GZIPInputStream(is);
        }

        if (indicator != null) {
          indicator.setText2(IdeBundle.message("progress.downloading.list.of.plugins"));
        }

        return readPluginsStream(is, indicator, PLUGIN_LIST_FILE);
      }
      finally {
        is.close();
      }
    }
    finally {
      connection.disconnect();
    }
  }

  private synchronized static List<IdeaPluginDescriptor> readPluginsStream(InputStream is,
                                                                           ProgressIndicator indicator,
                                                                           String file) throws Exception {
    File temp = createLocalPluginsDescriptions(file);

    OutputStream os = new FileOutputStream(temp, false);
    try {
      byte[] buffer = new byte[1024];
      int size;
      while ((size = is.read(buffer)) > 0) {
        os.write(buffer, 0, size);
        if (indicator != null) {
          indicator.checkCanceled();
        }
      }
    }
    finally {
      os.close();
    }

    return loadPluginList(temp);
  }

  private static List<IdeaPluginDescriptor> loadPluginList(File file) throws Exception {
    SAXParser parser = SAXParserFactory.newInstance().newSAXParser();
    RepositoryContentHandler handler = new RepositoryContentHandler();
    parser.parse(file, handler);
    return handler.getPluginsList();
  }

  private static File createLocalPluginsDescriptions(String file) throws IOException {
    File basePath = new File(PathManager.getPluginsPath());
    if (!basePath.isDirectory() && !basePath.mkdirs()) {
      throw new IOException("Cannot create directory: " + basePath);
    }

    File temp = new File(basePath, file);
    if (temp.exists()) {
      FileUtil.delete(temp);
    }
    FileUtil.createIfDoesntExist(temp);
    return temp;
  }

  public static List<IdeaPluginDescriptor> loadPluginsFromDescription(InputStream is, ProgressIndicator indicator) throws Exception {
    try {
      return readPluginsStream(is, indicator, "host.xml");
    }
    finally {
      is.close();
    }
  }

  public static String getDownloadUrl() {
    return ApplicationInfoImpl.getShadowInstance().getPluginsDownloadUrl() + "?action=download&id=";
  }
}
