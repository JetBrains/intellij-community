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
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.io.HttpRequests;
import com.intellij.util.PathUtil;
import com.intellij.util.net.NetUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URLConnection;
import java.util.List;

/**
 * @author stathik
 * @since Mar 28, 2003
 */
public class RepositoryHelper {
  @SuppressWarnings("SpellCheckingInspection") public static final String PLUGIN_LIST_FILE = "availables.xml";

  public static List<IdeaPluginDescriptor> loadPluginsFromRepository(@Nullable ProgressIndicator indicator) throws Exception {
    return loadPluginsFromRepository(indicator, null);
  }

  public static List<IdeaPluginDescriptor> loadPluginsFromRepository(@Nullable final ProgressIndicator indicator,
                                                                     @Nullable BuildNumber buildnumber) throws Exception {
    final ApplicationInfoEx appInfo = ApplicationInfoImpl.getShadowInstance();

    String url = appInfo.getPluginsListUrl() + "?build=" + (buildnumber != null ? buildnumber.asString() : appInfo.getApiVersion());

    if (indicator != null) {
      indicator.setText2(IdeBundle.message("progress.connecting.to.plugin.manager", appInfo.getPluginManagerUrl()));
    }

    final File pluginListFile = new File(PathManager.getPluginsPath(), PLUGIN_LIST_FILE);
    if (pluginListFile.length() > 0) {
      try {
        //noinspection SpellCheckingInspection
        url += "&crc32=" + Files.hash(pluginListFile, Hashing.crc32()).toString();
      }
      catch (NoSuchMethodError e) {
        String guavaPath = PathUtil.getJarPathForClass(Hashing.class);
        throw new RuntimeException(guavaPath, e);
      }
    }

    if (indicator != null) {
      indicator.setText2(IdeBundle.message("progress.waiting.for.reply.from.plugin.manager", appInfo.getPluginManagerUrl()));
    }

    return HttpRequests.request(url).connect(new HttpRequests.RequestProcessor<List<IdeaPluginDescriptor>>() {
      @Override
      public List<IdeaPluginDescriptor> process(@NotNull HttpRequests.Request request) throws IOException {
        if (indicator != null) {
          indicator.checkCanceled();
        }

        URLConnection connection = request.getConnection();
        if (connection instanceof HttpURLConnection &&
            ((HttpURLConnection)connection).getResponseCode() == HttpURLConnection.HTTP_NOT_MODIFIED) {
          return loadPluginList(pluginListFile);
        }

        if (indicator != null) {
          indicator.checkCanceled();
          indicator.setText2(IdeBundle.message("progress.downloading.list.of.plugins"));
        }

        synchronized (RepositoryHelper.class) {
          File localFile = createLocalPluginsDescriptions(PLUGIN_LIST_FILE);
          OutputStream output = new FileOutputStream(localFile);
          try {
            NetUtils.copyStreamContent(indicator, request.getInputStream(), output, connection.getContentLength());
            return loadPluginList(localFile);
          }
          finally {
            output.close();
          }
        }
      }
    });
  }

  private static List<IdeaPluginDescriptor> loadPluginList(File file) throws IOException{
    try {
      SAXParser parser = SAXParserFactory.newInstance().newSAXParser();
      RepositoryContentHandler handler = new RepositoryContentHandler();
      parser.parse(file, handler);
      return handler.getPluginsList();
    }
    catch (ParserConfigurationException e) {
      throw new IOException(e);
    }
    catch (SAXException e) {
      throw new IOException(e);
    }
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

  public static String getDownloadUrl() {
    return ApplicationInfoImpl.getShadowInstance().getPluginsDownloadUrl() + "?action=download&id=";
  }
}
