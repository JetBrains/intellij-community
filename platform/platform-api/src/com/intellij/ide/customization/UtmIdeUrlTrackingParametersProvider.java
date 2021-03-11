// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.customization;

import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.IdeUrlTrackingParametersProvider;
import com.intellij.openapi.diagnostic.Logger;
import org.apache.http.client.utils.URIBuilder;
import org.jetbrains.annotations.NotNull;

import java.net.URISyntaxException;

public final class UtmIdeUrlTrackingParametersProvider extends IdeUrlTrackingParametersProvider {
  private static final Logger LOG = Logger.getInstance(UtmIdeUrlTrackingParametersProvider.class);

  @Override
  public @NotNull String augmentUrl(@NotNull String originalUrl) {
    return augmentUrl(originalUrl, ApplicationInfo.getInstance().getBuild().getProductCode());
  }

  @Override
  public @NotNull String augmentUrl(@NotNull String originalUrl, @NotNull String campaignId) {
    try {
      return new URIBuilder(originalUrl)
        .addParameter("utm_source", "product")
        .addParameter("utm_medium", "link")
        .addParameter("utm_campaign", campaignId)
        .addParameter("utm_content", ApplicationInfo.getInstance().getShortVersion())
        .build().toString();
    }
    catch (URISyntaxException e) {
      LOG.warn(originalUrl, e);
      return originalUrl;
    }
  }
}
