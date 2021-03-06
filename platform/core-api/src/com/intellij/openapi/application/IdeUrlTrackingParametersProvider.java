// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application;

import org.jetbrains.annotations.NotNull;

/**
 * Allows IDEs to add tracking parameters to URLs which are opened from IDE and lead to company sites (e.g. download/update links, product documentation).
 * Override this service in your IDE to append tracking parameters to these URLs if you need to collect statistics on your site.
 * <br>
 * This service isn't supposed to be overridden in regular plugins.
 */
public class IdeUrlTrackingParametersProvider {
  public static IdeUrlTrackingParametersProvider getInstance() {
    return ApplicationManager.getApplication().getService(IdeUrlTrackingParametersProvider.class);
  }

  /**
   * @return {@code originalUrl} with appended parameters
   */
  public @NotNull String augmentUrl(@NotNull String originalUrl) {
    return originalUrl;
  }

  /**
   * @return {@code originalUrl} with appended parameters
   */
  public @NotNull String augmentUrl(@NotNull String originalUrl, String campaignId) {
    return originalUrl;
  }
}
