/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.ide.customization;

import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.IdeUrlTrackingParametersProvider;
import com.intellij.openapi.diagnostic.Logger;
import org.apache.http.client.utils.URIBuilder;
import org.jetbrains.annotations.NotNull;

import java.net.URISyntaxException;

/**
 * @author nik
 */
public class UtmIdeUrlTrackingParametersProvider extends IdeUrlTrackingParametersProvider {
  private static final Logger LOG = Logger.getInstance(UtmIdeUrlTrackingParametersProvider.class);

  @NotNull
  @Override
  public String augmentUrl(@NotNull String originalUrl) {
    try {
      ApplicationInfo info = ApplicationInfo.getInstance();
      String productVersion = info.getMajorVersion() + "." + info.getMinorVersionMainPart();
      return new URIBuilder(originalUrl).addParameter("utm_source", "product")
                                        .addParameter("utm_medium", "link")
                                        .addParameter("utm_campaign", info.getBuild().getProductCode())
                                        .addParameter("utm_content", productVersion)
                                        .build().toString();
    }
    catch (URISyntaxException e) {
      LOG.warn(originalUrl, e);
      return originalUrl;
    }
  }
}
