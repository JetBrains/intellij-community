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
package com.intellij.facet.frameworks;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtilRt;
import com.intellij.util.io.HttpRequests;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

public abstract class SettingsConnectionService {
  private static final Logger LOG = Logger.getInstance("com.intellij.facet.frameworks.SettingsConnectionService");

  protected static final String SERVICE_URL_ATTR_NAME = "url";

  private Map<String, String> myAttributesMap;

  @NotNull
  protected String[] getAttributeNames() {
    return new String[]{SERVICE_URL_ATTR_NAME};
  }

  private final String mySettingsUrl;
  @Nullable
  private final String myDefaultServiceUrl;

  protected SettingsConnectionService(@NotNull String settingsUrl, @Nullable String defaultServiceUrl) {
    mySettingsUrl = settingsUrl;
    myDefaultServiceUrl = defaultServiceUrl;
  }

  @SuppressWarnings("unused")
  @Deprecated
  public String getSettingsUrl() {
    return mySettingsUrl;
  }

  @Nullable
  public String getDefaultServiceUrl() {
    return myDefaultServiceUrl;
  }

  @Nullable
  private Map<String, String> readSettings(final String... attributes) {
    return HttpRequests.request(mySettingsUrl)
      .productNameAsUserAgent()
      .connect(new HttpRequests.RequestProcessor<Map<String, String>>() {
        @Override
        public Map<String, String> process(@NotNull HttpRequests.Request request) throws IOException {
          Map<String, String> settings = ContainerUtilRt.newLinkedHashMap();
          try {
            Element root = JDOMUtil.load(request.getReader());
            for (String s : attributes) {
              String attributeValue = root.getAttributeValue(s);
              if (StringUtil.isNotEmpty(attributeValue)) {
                settings.put(s, attributeValue);
              }
            }
          }
          catch (JDOMException e) {
            LOG.error(e);
          }
          return settings;
        }
      }, Collections.<String, String>emptyMap(), LOG);
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
