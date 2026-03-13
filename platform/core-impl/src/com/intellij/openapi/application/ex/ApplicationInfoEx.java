// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.ex;

import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.BuildNumber;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Calendar;
import java.util.List;

public abstract class ApplicationInfoEx extends ApplicationInfo {
  public static ApplicationInfoEx getInstanceEx() {
    return (ApplicationInfoEx)getInstance();
  }

  public abstract @NotNull Calendar getMajorReleaseBuildDate();

  /**
   * Returns a path to an SVG icon of the product.
   * The path is a relative path inside the product's JAR files.
   * Please note that release and EAP builds may have different icons.
   */
  public abstract @NotNull String getApplicationSvgIconUrl();

  /**
   * Returns a path to an SVG file,
   * containing a variant of {@link #getApplicationSvgIconUrl() the product icon} which is suitable for 16x16 images.
   */
  public abstract @NotNull String getSmallApplicationSvgIconUrl();

  public abstract String getCopyrightStart();

  /**
   * Returns {@code true} only for EAP builds of "major" releases (i.e. for {@code 2018.3}, but not for {@code 2018.3.1}).
   */
  public abstract boolean isMajorEAP();

  @ApiStatus.Experimental
  public abstract boolean isPreview();

  /**
   * Returns the full IDE's product code (as used in build numbers) if this application is a frontend variant (JetBrains Client) of it.
   * Otherwise, returns {@code null}.
   */
  @ApiStatus.Internal
  public abstract @Nullable String getFullIdeProductCode();

  /**
   * Returns URL to plugins repository without a trailing slash.
   */
  public abstract @NotNull String getPluginManagerUrl();

  public abstract boolean usesJetBrainsPluginRepository();

  public abstract @NotNull String getPluginsListUrl();

  public abstract @NotNull String getPluginDownloadUrl();

  public abstract String getSubscriptionFormId();

  public abstract boolean areSubscriptionTipsAvailable();

  /**
   * @return {@code true} if the product's vendor is JetBrains
   */
  public final boolean isVendorJetBrains() {
    return "JetBrains".equals(getShortCompanyName());
  }

  public abstract @NotNull BuildNumber getApiVersionAsNumber();

  public abstract @NotNull List<PluginId> getEssentialPluginIds();

  public abstract @Nullable String getDefaultLightLaf();

  public abstract @Nullable String getDefaultClassicLightLaf();

  public abstract @Nullable String getDefaultDarkLaf();

  public abstract @Nullable String getDefaultClassicDarkLaf();
}
