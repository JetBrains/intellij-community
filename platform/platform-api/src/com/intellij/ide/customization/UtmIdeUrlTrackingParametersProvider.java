// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.customization;

import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.IdeUrlTrackingParametersProvider;
import com.intellij.openapi.diagnostic.Logger;
import org.apache.http.client.utils.URIBuilder;
import org.jetbrains.annotations.NotNull;

import java.net.URISyntaxException;

public final class UtmIdeUrlTrackingParametersProvider extends IdeUrlTrackingParametersProvider {
  @Override
  public @NotNull String augmentUrl(@NotNull String originalUrl) {
    return augmentUrl(originalUrl, ApplicationInfo.getInstance().getBuild().getProductCode());
  }

  @Override
  public @NotNull String augmentUrl(@NotNull String originalUrl, @NotNull String campaignId) {
    try {
      return new URIBuilder(originalUrl)
        .setParameter("utm_source", "product")
        .setParameter("utm_medium", "link")
        .setParameter("utm_campaign", campaignId)
        .setParameter("utm_content", ApplicationInfo.getInstance().getShortVersion())
        .build().toString();
    }
    catch (URISyntaxException e) {
      Logger.getInstance(UtmIdeUrlTrackingParametersProvider.class).warn(originalUrl, e);
      return originalUrl;
    }
  }
}
