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
import com.intellij.openapi.util.BuildNumber;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.net.HttpConfigurable;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
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

  public static ArrayList<IdeaPluginDescriptor> process(JLabel label) throws IOException, ParserConfigurationException, SAXException {
    ArrayList<IdeaPluginDescriptor> plugins = null;
    try {
      BuildNumber buildNumber = ApplicationInfo.getInstance().getBuild();
      @NonNls String url = getListUrl() + "?build=" + buildNumber.asString();

      setLabelText(label, IdeBundle.message("progress.connecting.to.plugin.manager", getRepositoryHost()));
      HttpConfigurable.getInstance().prepareURL(getRepositoryHost());
//      if( !pi.isCanceled() )
      {
        RepositoryContentHandler handler = new RepositoryContentHandler();
        HttpURLConnection connection = (HttpURLConnection)new URL(url).openConnection();

        setLabelText(label, IdeBundle.message("progress.waiting.for.reply.from.plugin.manager", getRepositoryHost()));

        InputStream is = getConnectionInputStream(connection);
        if (is != null) {
          setLabelText(label, IdeBundle.message("progress.downloading.list.of.plugins"));
          File temp = createLocalPluginsDescriptions();
          readPluginsStream(temp, is, handler);

          plugins = handler.getPluginsList();
        }
      }
    }
    catch (RuntimeException e) {
      e.printStackTrace();
      if (e.getCause() == null || !(e.getCause() instanceof InterruptedException)) {
      }
    }
    return plugins;
  }

  private static void setLabelText(final JLabel label, final String message) {
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      public void run() {
        label.setText(message);
      }
    });
  }


  public static InputStream getConnectionInputStream(URLConnection connection) {
    try {
      return connection.getInputStream();
    }
    catch (IOException e) {
      return null;
    }
  }

  public static File createLocalPluginsDescriptions() throws IOException {
    File basePath = new File(PathManager.getPluginsPath());
    basePath.mkdirs();

    File temp = new File(basePath, extPluginsFile);
    if (temp.exists()) {
      FileUtil.delete(temp);
    }
    FileUtil.createIfDoesntExist(temp);
    return temp;
  }

  public static void readPluginsStream(File temp, InputStream is, RepositoryContentHandler handler)
    throws SAXException, IOException, ParserConfigurationException {
    SAXParser parser = SAXParserFactory.newInstance().newSAXParser();
    FileOutputStream fos = null;
    try {
      fos = new FileOutputStream(temp, false);
      byte[] buffer = new byte[1024];
      do {
        int size = is.read(buffer);
        if (size == -1) break;
        fos.write(buffer, 0, size);
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
