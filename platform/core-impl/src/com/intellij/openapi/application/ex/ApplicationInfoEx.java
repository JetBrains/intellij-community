// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application.ex;

import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.BuildNumber;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Calendar;
import java.util.List;

public abstract class ApplicationInfoEx extends ApplicationInfo {
  public static ApplicationInfoEx getInstanceEx() {
    return (ApplicationInfoEx)getInstance();
  }

  public abstract Calendar getMajorReleaseBuildDate();

  public abstract String getSplashImageUrl();

  public abstract String getAboutImageUrl();

  /**
   * @deprecated use {@link #getApplicationSvgIconUrl()} instead
   */
  @Deprecated
  public abstract String getIconUrl();

  /**
   * @deprecated use {@link #getSmallApplicationSvgIconUrl()} instead
   */
  @Deprecated
  public abstract @NotNull String getSmallIconUrl();

  /**
   * @deprecated use {@link #getApplicationSvgIconUrl()} instead
   */
  @Deprecated
  public abstract @Nullable String getBigIconUrl();

  /**
   * Return path to an svg file containing icon of the current version of the product. The path is a relative path inside the product's JAR
   * files. It may return special icon for EAP builds.
   */
  public abstract @Nullable String getApplicationSvgIconUrl();

  /**
   * Return path to an svg file containing a variant of {@link #getApplicationSvgIconUrl() the product icon} which is suitable for 16x16 images.
   */
  public abstract @Nullable String getSmallApplicationSvgIconUrl();

  public abstract String getToolWindowIconUrl();

  public abstract @Nullable String getWelcomeScreenLogoUrl();

  /**
   * This method is used to detect that the product isn't meant to be used as an IDE but is embedded to another product or used as a
   * standalone tool so different licensing scheme should be applied.
   */
  public abstract @Nullable String getPackageCode();

  public abstract boolean showLicenseeInfo();

  public abstract String getCopyrightStart();

  public abstract boolean isEAP();

  /**
   * Returns {@code true} only for EAP builds of "major" releases (i.e. for 2018.3, but not for 2018.3.1).
   */
  public abstract boolean isMajorEAP();

  public abstract @Nullable UpdateUrls getUpdateUrls();

  public abstract String getDocumentationUrl();

  public abstract String getSupportUrl();

  public abstract String getYoutrackUrl();

  public abstract String getFeedbackUrl();

  /*
   * Returns url to plugins repository without trailing slash
   */
  public abstract String getPluginManagerUrl();

  public abstract boolean usesJetBrainsPluginRepository();

  public abstract String getPluginsListUrl();

  public abstract String getChannelsListUrl();

  public abstract String getPluginsDownloadUrl();

  public abstract String getBuiltinPluginsUrl();

  public abstract String getWebHelpUrl();

  public abstract String getWhatsNewUrl();

  public abstract String getWinKeymapUrl();

  public abstract String getMacKeymapUrl();

  public abstract long getAboutForeground();

  public abstract long getAboutLinkColor();

  public interface UpdateUrls {
    String getCheckingUrl();
    String getPatchesUrl();
  }

  /**
   * @return {@code true} if the specified plugin is an essential part of the IDE so it cannot be disabled and isn't shown in Settings | Plugins
   */
  public abstract boolean isEssentialPlugin(@NotNull String pluginId);

  public abstract boolean isEssentialPlugin(@NotNull PluginId pluginId);

  public abstract @Nullable String getCustomizeIDEWizardStepsProvider();

  public abstract int @Nullable [] getAboutLogoRect();

  public abstract String getSubscriptionFormId();

  public abstract String getSubscriptionNewsKey();

  public abstract String getSubscriptionNewsValue();

  public abstract String getSubscriptionTipsKey();

  public abstract boolean areSubscriptionTipsAvailable();

  public abstract @Nullable String getSubscriptionAdditionalFormData();

  /**
   * @return true if the product's vendor is JetBrains
   */
  public final boolean isVendorJetBrains() {
    return "JetBrains".equals(getShortCompanyName());
  }

  public abstract List<ProgressSlide> getProgressSlides();

  public abstract int getProgressHeight();

  public abstract int getProgressY();

  public abstract long getProgressColor();

  public abstract long getCopyrightForeground();

  public abstract @Nullable String getProgressTailIcon();

  public abstract @NotNull BuildNumber getApiVersionAsNumber();
}