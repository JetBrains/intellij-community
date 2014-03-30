/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import com.intellij.util.containers.hash.HashMap;
import org.jdom.Document;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Map;

public abstract class SettingsConnectionService {
  private static final Logger LOG = Logger.getInstance("com.intellij.facet.frameworks.SettingsConnectionService");

  protected static final String SERVICE_URL_ATTR_NAME = "url";

  private static final String myAgentID = "IntelliJ IDEA";

  private Map<String, String> myAttributesMap;

  @NotNull
  protected String[] getAttributeNames() {
       return new String[] {SERVICE_URL_ATTR_NAME};
  }

  private final String mySettingsUrl;
  @Nullable private final String myDefaultServiceUrl;

  protected SettingsConnectionService(@NotNull String settingsUrl, @Nullable String defaultServiceUrl) {
    mySettingsUrl = settingsUrl;
    myDefaultServiceUrl = defaultServiceUrl;
  }

  public String getSettingsUrl() {
    return mySettingsUrl;
  }

  @Nullable
  public String getDefaultServiceUrl() {
    return myDefaultServiceUrl;
  }

  @Nullable
  private Map<String, String> readSettings(String... attributes) {
    Map<String, String> settings = new HashMap<String, String>();
    try {
      final URL url = new URL(getSettingsUrl());
      final InputStream is = getStream(url);
      final Document document = JDOMUtil.loadDocument(is);
      final Element root = document.getRootElement();
      for (String s : attributes) {
        final String attributeValue = root.getAttributeValue(s);
        if (StringUtil.isNotEmpty(attributeValue)) {
          settings.put(s, attributeValue);
        }
      }
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

    return settings;
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
    final String serviceUrl = getSettingValue(SERVICE_URL_ATTR_NAME);

    return serviceUrl == null ? getDefaultServiceUrl() : serviceUrl;
  }

  @Nullable
  protected String getSettingValue(@NotNull String attributeValue) {
    if (myAttributesMap == null) {
      myAttributesMap = readSettings(getAttributeNames());
    }
    return myAttributesMap != null ? myAttributesMap.get(attributeValue) : null;
  }
}
