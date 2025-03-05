// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.facet.frameworks;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.io.HttpRequests;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public abstract class SettingsConnectionService {
  private static final Logger LOG = Logger.getInstance(SettingsConnectionService.class);

  protected static final String SERVICE_URL_ATTR_NAME = "url";

  private Map<String, String> myAttributesMap;

  protected String @NotNull [] getAttributeNames() {
    return new String[]{SERVICE_URL_ATTR_NAME};
  }

  private final @Nullable String mySettingsUrl;
  private final @Nullable String myDefaultServiceUrl;

  protected SettingsConnectionService(@Nullable String settingsUrl, @Nullable String defaultServiceUrl) {
    mySettingsUrl = settingsUrl;
    myDefaultServiceUrl = defaultServiceUrl;
  }

  public @Nullable String getDefaultServiceUrl() {
    return myDefaultServiceUrl;
  }

  private @Nullable Map<String, String> readSettings(final String... attributes) {
    if (mySettingsUrl == null) return Collections.emptyMap();
    return HttpRequests.request(mySettingsUrl)
      .productNameAsUserAgent()
      .connect(request -> {
        Map<String, String> settings = new LinkedHashMap<>();
        try {
          Element root = JDOMUtil.load(request.getInputStream());
          for (String s : attributes) {
            String attributeValue = root.getAttributeValue(s);
            if (StringUtil.isNotEmpty(attributeValue)) {
              settings.put(s, attributeValue);
            }
          }
        }
        catch (JDOMException e) {
          LOG.info(e);
        }
        return settings;
      }, Collections.emptyMap(), LOG);
  }

  public @Nullable String getServiceUrl() {
    final String serviceUrl = getSettingValue(SERVICE_URL_ATTR_NAME);
    return serviceUrl == null ? getDefaultServiceUrl() : serviceUrl;
  }

  protected @Nullable String getSettingValue(@NotNull String attributeValue) {
    if (myAttributesMap == null || myAttributesMap.isEmpty()) {
      myAttributesMap = readSettings(getAttributeNames());
    }
    return myAttributesMap != null ? myAttributesMap.get(attributeValue) : null;
  }
}