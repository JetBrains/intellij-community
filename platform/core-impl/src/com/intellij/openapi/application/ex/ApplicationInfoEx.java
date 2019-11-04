// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application.ex;

import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.extensions.PluginId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.Calendar;
import java.util.List;

public abstract class ApplicationInfoEx extends ApplicationInfo {
  public static ApplicationInfoEx getInstanceEx() {
    return (ApplicationInfoEx) getInstance();
  }

  public abstract Calendar getMajorReleaseBuildDate();

  public abstract String getSplashImageUrl();

  public abstract Color getSplashTextColor();

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
  @NotNull
  public abstract String getSmallIconUrl();

  /**
   * @deprecated use {@link #getApplicationSvgIconUrl()} instead
   */
  @Deprecated
  @Nullable
  public abstract String getBigIconUrl();

  /**
   * Return path to an svg file containing icon of the current version of the product. The path is a relative path inside the product's JAR
   * files. It may return special icon for EAP builds.
   */
  @Nullable
  public abstract String getApplicationSvgIconUrl();

  /**
   * Return path to an svg file containing a variant of {@link #getApplicationSvgIconUrl() the product icon} which is suitable for 16x16 images.
   */
  @Nullable
  public abstract String getSmallApplicationSvgIconUrl();

  /**
   * Return an svg file containing icon of the current version of the product. It may return special icon for EAP builds.
   */
  @Nullable
  public abstract File getApplicationSvgIconFile();

  public abstract String getToolWindowIconUrl();

  public abstract String getWelcomeScreenLogoUrl();

  /**
   * This method is used to detect that the product isn't meant to be used as an IDE but is embedded to another product or used as a
   * standalone tool so different licensing scheme should be applied.
   */
  @Nullable
  public abstract String getPackageCode();

  public abstract boolean showLicenseeInfo();

  public abstract boolean isEAP();

  /**
   * Returns {@code true} only for EAP builds of "major" releases (i.e. for 2018.3, but not for 2018.3.1).
   */
  public abstract boolean isMajorEAP();

  public abstract UpdateUrls getUpdateUrls();

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

  public abstract Color getAboutForeground();

  public interface UpdateUrls {
    String getCheckingUrl();
    String getPatchesUrl();
  }

  /**
   * @return {@code true} if the specified plugin is an essential part of the IDE so it cannot be disabled and isn't shown in Settings | Plugins
   */
  public abstract boolean isEssentialPlugin(@NotNull String pluginId);

  public abstract boolean isEssentialPlugin(@NotNull PluginId pluginId);

  @Nullable
  public abstract String getCustomizeIDEWizardStepsProvider();

  public abstract String getSubscriptionFormId();

  public abstract String getSubscriptionNewsKey();

  public abstract String getSubscriptionNewsValue();

  public abstract String getSubscriptionTipsKey();

  public abstract boolean areSubscriptionTipsAvailable();

  @Nullable
  public abstract String getSubscriptionAdditionalFormData();

  /**
   * @return true if the product's vendor is JetBrains
   */
  public final boolean isVendorJetBrains() {
    return "JetBrains".equals(getShortCompanyName());
  }

  public abstract List<ProgressSlide> getProgressSlides();

  public abstract int getProgressHeight();

  public abstract int getProgressY();

  @Nullable
  public abstract Color getProgressColor();

  @Nullable
  public abstract Icon getProgressTailIcon();

  public abstract int getLicenseOffsetX();

  public abstract int getLicenseOffsetY();
}
