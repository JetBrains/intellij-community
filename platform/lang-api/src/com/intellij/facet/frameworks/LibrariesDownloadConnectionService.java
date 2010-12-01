/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.facet.frameworks;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.jdom.Document;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

public class LibrariesDownloadConnectionService {
  private static final Logger LOG = Logger.getInstance("com.intellij.facet.frameworks.LibrariesDownloadConnectionService");

  private static final String DOWNLOAD_SERVICE_SETTINGS_URL = "http://jetbrains.com/idea/download-assistant.xml ";
  private static final String SERVICE_URL_ATTR_NAME = "url";

  private static final String myAgentID = "IntelliJ IDEA";

  private static final LibrariesDownloadConnectionService myInstance = new LibrariesDownloadConnectionService();
  private String myServiceUrl;

  public static LibrariesDownloadConnectionService getInstance() {
    return myInstance;
  }

  private LibrariesDownloadConnectionService() {
  }

  private static String readServiceUrl() {
    try {
      final URL url = new URL(DOWNLOAD_SERVICE_SETTINGS_URL);
      final InputStream is = getStream(url);
      final Document document = JDOMUtil.loadDocument(is);
      final Element root = document.getRootElement();
      return StringUtil.notNullize(root.getAttributeValue(SERVICE_URL_ATTR_NAME), "");
    }
    catch (MalformedURLException e) {
      LOG.error(e);
    }
    catch (IOException e) {
      // no route to host, unknown host, etc.
    }
    catch (Exception e) {
      LOG.error(e);
    }
    // default
    return "http://frameworks.jetbrains.com";
  }

  private static InputStream getStream(URL url) throws IOException {
    final URLConnection connection = url.openConnection();
    if (connection instanceof HttpURLConnection) {
      connection.setRequestProperty("User-agent", myAgentID);
    }
    return connection.getInputStream();
  }

  @Nullable
  public String getServiceUrl() {
    if (myServiceUrl == null) {
      myServiceUrl = readServiceUrl();
    }
    return myServiceUrl;
  }
}
