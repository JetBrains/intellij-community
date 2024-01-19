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

  /** @deprecated please use {@link #getApplicationSvgIconUrl()} instead. */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public abstract @Nullable String getWelcomeScreenLogoUrl();

  public abstract String getCopyrightStart();

  /**
   * Returns {@code true} only for EAP builds of "major" releases (i.e. for {@code 2018.3}, but not for {@code 2018.3.1}).
   */
  public abstract boolean isMajorEAP();

  @ApiStatus.Experimental
  public abstract boolean isPreview();

  /**
   * @deprecated use {@link com.intellij.platform.ide.customization.ExternalProductResourceUrls#getDownloadPageUrl()} instead
   */
  @ApiStatus.ScheduledForRemoval
  @Deprecated
  public final @Nullable String getDownloadUrl() {
    String productUrl = getProductUrl();
    return productUrl != null ? productUrl + "download/" : null;
  }

  public abstract @Nullable UpdateUrls getUpdateUrls();

  /**
   * @deprecated use {@link com.intellij.platform.ide.customization.ExternalProductResourceUrls#getGettingStartedPageUrl()} instead
   */
  @ApiStatus.ScheduledForRemoval
  @Deprecated
  public abstract String getDocumentationUrl();

  /**
   * @deprecated use {@link com.intellij.platform.ide.customization.ExternalProductResourceUrls#getTechnicalSupportUrl()} instead
   */
  @ApiStatus.ScheduledForRemoval
  @Deprecated
  public abstract String getSupportUrl();

  /**
   * @deprecated use {@link com.intellij.platform.ide.customization.ExternalProductResourceUrls#getBugReportUrl()} instead
   */
  @ApiStatus.ScheduledForRemoval
  @Deprecated
  public abstract String getYoutrackUrl();

  /**
   * @deprecated use {@link com.intellij.platform.ide.customization.ExternalProductResourceUrls#getFeedbackReporter()} instead
   */
  @ApiStatus.ScheduledForRemoval
  @Deprecated
  public abstract String getFeedbackUrl();

  /**
   * Returns URL to plugins repository without a trailing slash.
   */
  public abstract @NotNull String getPluginManagerUrl();

  public abstract boolean usesJetBrainsPluginRepository();

  public abstract @NotNull String getPluginsListUrl();

  /**
   * @deprecated is not used by the platform anymore
   */
  @ApiStatus.ScheduledForRemoval
  @Deprecated
  public abstract String getChannelListUrl();

  public abstract @NotNull String getPluginDownloadUrl();

  /**
   * @deprecated use {@link com.intellij.ide.plugins.RepositoryHelper#CUSTOM_BUILT_IN_PLUGIN_REPOSITORY_PROPERTY} system property to pass
   * URL of the built-in custom plugin repository instead
   */
  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated
  public abstract String getBuiltinPluginsUrl();

  /**
   * @deprecated use {@link com.intellij.platform.ide.customization.ExternalProductResourceUrls#getHelpPageUrl()} instead
   */
  @ApiStatus.ScheduledForRemoval
  @Deprecated
  public abstract String getWebHelpUrl();

  /**
   * @deprecated use {@link com.intellij.platform.ide.customization.ExternalProductResourceUrls#getWhatIsNewPageUrl()} instead
   */
  @ApiStatus.ScheduledForRemoval
  @Deprecated
  public abstract String getWhatsNewUrl();

  /**
   * @deprecated use {@link com.intellij.ide.actions.WhatsNewUtil#isWhatsNewAvailable()} instead
   */
  @ApiStatus.ScheduledForRemoval
  @Deprecated
  public abstract boolean isShowWhatsNewOnUpdate();

  public abstract @Nullable String getWinKeymapUrl();

  public abstract @Nullable String getMacKeymapUrl();

  public interface UpdateUrls {
    /**
     * @deprecated use {@link com.intellij.platform.ide.customization.ExternalProductResourceUrls#getUpdateMetadataUrl()} instead
     */
    @ApiStatus.ScheduledForRemoval
    @Deprecated
    String getCheckingUrl();

    /**
     * @deprecated use {@link com.intellij.platform.ide.customization.ExternalProductResourceUrls#computePatchUrl} instead
     */
    @ApiStatus.ScheduledForRemoval
    @Deprecated
    String getPatchesUrl();
  }

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
