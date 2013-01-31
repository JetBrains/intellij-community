/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.net.HttpConfigurable;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.xml.sax.SAXException;

import javax.swing.*;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.zip.GZIPInputStream;

/**
 * Created by IntelliJ IDEA.
 * User: stathik
 * Date: Mar 28, 2003
 * Time: 12:56:26 AM
 * To change this template use Options | File Templates.
 */
public class RepositoryHelper {
  @NonNls public static final String DOWNLOAD_URL = getDownloadUrl() + "?action=download&id=";

  @NonNls private static final String FILENAME = "filename=";
  @NonNls public static final String extPluginsFile = "availables.xml";

  public static ArrayList<IdeaPluginDescriptor> process(@Nullable ProgressIndicator indicator) throws IOException, ParserConfigurationException, SAXException {
    BuildNumber buildNumber = ApplicationInfo.getInstance().getBuild();
    @NonNls String url = getListUrl() + "?build=" + buildNumber.asString();

    if (indicator != null) {
      indicator.setText2(IdeBundle.message("progress.connecting.to.plugin.manager", getRepositoryHost()));
    }

    HttpURLConnection connection = HttpConfigurable.getInstance().openHttpConnection(url);
    connection.setRequestProperty("Accept-Encoding", "gzip");

    if (indicator != null) {
      indicator.setText2(IdeBundle.message("progress.waiting.for.reply.from.plugin.manager", getRepositoryHost()));
    }

    connection.connect();

    try {
      if (indicator != null) {
        indicator.checkCanceled();
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

        RepositoryContentHandler handler = new RepositoryContentHandler();

        readPluginsStream( is, handler, indicator, extPluginsFile);

        return handler.getPluginsList();
      }
      finally {
        is.close();
      }
    }
    finally {
      connection.disconnect();
    }
  }

  private static void setLabelText(@Nullable final JLabel label, final String message) {
    if (label == null) return;
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      public void run() {
        label.setText(message);
      }
    });
  }

  private static File createLocalPluginsDescriptions(final String file) throws IOException {
    File basePath = new File(PathManager.getPluginsPath());
    basePath.mkdirs();

    File temp = new File(basePath, file);
    if (temp.exists()) {
      FileUtil.delete(temp);
    }
    FileUtil.createIfDoesntExist(temp);
    return temp;
  }

  private static void readPluginsStream(InputStream is, RepositoryContentHandler handler, ProgressIndicator indicator, final String file)
    throws SAXException, IOException, ParserConfigurationException, ProcessCanceledException {
    File temp = createLocalPluginsDescriptions(file);
    SAXParser parser = SAXParserFactory.newInstance().newSAXParser();
    FileOutputStream fos = null;
    try {
      fos = new FileOutputStream(temp, false);
      byte[] buffer = new byte[1024];
      do {
        int size = is.read(buffer);
        if (size == -1) break;
        fos.write(buffer, 0, size);

        if (indicator != null) {
          indicator.checkCanceled();
        }
      }
      while (true);
      fos.close();
      fos = null;

      parser.parse(temp, handler);
    }
    finally {
      if (fos != null) {
        fos.close();
      }
    }
  }

  public static ArrayList<IdeaPluginDescriptor> loadPluginsFromDescription(InputStream inputStream, ProgressIndicator indicator) throws SAXException, IOException, ParserConfigurationException {
    try {
      RepositoryContentHandler handler = new RepositoryContentHandler();
      readPluginsStream(inputStream, handler, indicator, "host.xml");
      return handler.getPluginsList();
    }
    finally {
      inputStream.close();
    }
  }

  public static String getRepositoryHost() {
    return ApplicationInfoImpl.getShadowInstance().getPluginManagerUrl();
  }

  public static String getListUrl() {
    return ApplicationInfoImpl.getShadowInstance().getPluginsListUrl();
  }

  public static String getDownloadUrl() {
    return ApplicationInfoImpl.getShadowInstance().getPluginsDownloadUrl();
  }
}
