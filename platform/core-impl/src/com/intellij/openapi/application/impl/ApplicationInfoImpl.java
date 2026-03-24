// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl;

import com.intellij.ReviseWhenPortedToJDK;
import com.intellij.diagnostic.Activity;
import com.intellij.diagnostic.StartUpMeasurer;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.IdeUrlTrackingParametersProvider;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.serviceContainer.NonInjectable;
import com.intellij.util.xml.dom.XmlElement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.annotations.VisibleForTesting;

import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * Provides access to content of *ApplicationInfo.xml file.
 * The scheme for *ApplicationInfo.xml files is defined in platform/platform-resources/src/idea/ApplicationInfo.xsd,
 * so you need to update it when adding or removing support for some XML elements in this class.
 */
@ApiStatus.Internal
public final class ApplicationInfoImpl extends ApplicationInfoEx {
  public static final String DEFAULT_PLUGINS_HOST = "https://plugins.jetbrains.com";
  public static final String IDEA_PLUGINS_HOST_PROPERTY = "idea.plugins.host";

  @ApiStatus.Experimental
  public static final String SUBSCRIPTION_MODE_SPLASH_MARKER_FILE_NAME = "splash-subscription-mode.txt";
  @ApiStatus.Experimental
  public static final String SIMPLIFIED_SPLASH_MARKER_FILE_NAME = "splash-simplified.txt";

  private static final String IDEA_APPLICATION_INFO_DEFAULT_DARK_LAF = "idea.application.info.default.dark.laf";
  private static final String IDEA_APPLICATION_INFO_DEFAULT_CLASSIC_DARK_LAF = "idea.application.info.default.classic.dark.laf";
  private static final String IDEA_APPLICATION_INFO_DEFAULT_LIGHT_LAF = "idea.application.info.default.light.laf";
  private static final String IDEA_APPLICATION_INFO_DEFAULT_CLASSIC_LIGHT_LAF = "idea.application.info.default.classic.light.laf";

  private static volatile ApplicationInfoImpl instance;

  private String myCodeName;
  private String myMajorVersion;
  private String myMinorVersion;
  private String myMicroVersion;
  private String myPatchVersion;
  private String myFullVersionFormat;
  private String myBuildNumber;
  private @Nullable String myBuildBranchName;
  private String myApiVersion;
  private String myVersionSuffix;
  private String myCompanyName = "JetBrains s.r.o.";
  private String myCopyrightStart = "2000";
  private String myShortCompanyName;
  private String myCompanyUrl = "https://www.jetbrains.com/";
  private @Nullable String splashImageUrl;
  private @Nullable String simplifiedSplashImageUrl;
  private @Nullable String subscriptionModeSplashImageUrl;
  private @Nullable String eapSplashImageUrl;
  private String svgIconUrl;
  private String mySvgEapIconUrl;
  private String mySmallSvgIconUrl;
  private String mySmallSvgEapIconUrl;

  private ZonedDateTime buildTime;
  private ZonedDateTime majorReleaseBuildDate;
  private String myPluginManagerUrl;
  private String myPluginsListUrl;
  private String pluginDownloadUrl;
  private boolean isEap;
  private final List<PluginId> essentialPluginIds = new ArrayList<>();

  private String mySubscriptionFormId;
  private boolean mySubscriptionTipsAvailable;

  private String myDefaultLightLaf;
  private String myDefaultClassicLightLaf;
  private String myDefaultDarkLaf;
  private String myDefaultClassicDarkLaf;

  private static final Logger LOG = Logger.getInstance(ApplicationInfoImpl.class);

  // if application loader was not used
  @SuppressWarnings("unused")
  private ApplicationInfoImpl() {
    this(ApplicationNamesInfo.initAndGetRawData());
  }

  @NonInjectable
  @ApiStatus.Internal
  @VisibleForTesting
  public ApplicationInfoImpl(@NotNull XmlElement element) {
    // the behavior of this method must be consistent with the `idea/ApplicationInfo.xsd` schema
    for (XmlElement child : element.children) {
      switch (child.name) {
        case "version": {
          myMajorVersion = child.getAttributeValue("major");
          myMinorVersion = child.getAttributeValue("minor");
          myMicroVersion = child.getAttributeValue("micro");
          myPatchVersion = child.getAttributeValue("patch");
          myFullVersionFormat = child.getAttributeValue("full");
          myCodeName = child.getAttributeValue("codename");
          isEap = Boolean.parseBoolean(child.getAttributeValue("eap"));
          myVersionSuffix = child.getAttributeValue("suffix", isEap ? "EAP" : null);
        }
        break;

        case "company": {
          myCompanyName = child.getAttributeValue("name", myCompanyName);
          myShortCompanyName = child.getAttributeValue("shortName", myCompanyName == null ? null : shortenCompanyName(myCompanyName));
          myCompanyUrl = child.getAttributeValue("url", myCompanyUrl);
          myCopyrightStart = child.getAttributeValue("copyrightStart", myCopyrightStart);
        }
        break;

        case "build": {
          readBuildInfo(child);
        }
        break;

        case "logo": {
          splashImageUrl = getAttributeValue(child, "url");
        }
        break;

        case "logo-eap": {
          eapSplashImageUrl = getAttributeValue(child, "url");
        }
        break;

        case "logo-subscription-mode": {
          subscriptionModeSplashImageUrl = getAttributeValue(child, "url");
        }
        break;

        case "logo-simplified": {
          simplifiedSplashImageUrl = getAttributeValue(child, "url");
        }
        break;

        case "icon": {
          svgIconUrl = child.getAttributeValue("svg");
          mySmallSvgIconUrl = child.getAttributeValue("svg-small");
        }
        break;

        case "icon-eap": {
          mySvgEapIconUrl = child.getAttributeValue("svg");
          mySmallSvgEapIconUrl = child.getAttributeValue("svg-small");
        }
        break;

        case "plugins": {
          readPluginInfo(child);
        }
        break;

        case "essential-plugin": {
          String id = child.content;
          if (id != null && !id.isEmpty()) {
            essentialPluginIds.add(PluginId.getId(id));
          }
        }
        break;

        case "subscriptions": {
          //noinspection SpellCheckingInspection
          mySubscriptionFormId = child.getAttributeValue("formid");
          mySubscriptionTipsAvailable = Boolean.parseBoolean(child.getAttributeValue("tips-available"));
        }
        break;

        case "default-laf": {
          String laf = getAttributeValue(child, "light");
          if (laf != null) {
            myDefaultLightLaf = laf.trim();
          }

          laf = getAttributeValue(child, "light-classic");
          if (laf != null) {
            myDefaultClassicLightLaf = laf.trim();
          }

          laf = getAttributeValue(child, "dark");
          if (laf != null) {
            myDefaultDarkLaf = laf.trim();
          }

          laf = getAttributeValue(child, "dark-classic");
          if (laf != null) {
            myDefaultClassicDarkLaf = laf.trim();
          }
        }

        break;
      }
    }

    if (myPluginManagerUrl == null) {
      readPluginInfo(null);
    }

    requireNonNull(svgIconUrl, "Missing attribute: //icon@svg");
    requireNonNull(mySmallSvgIconUrl, "Missing attribute: //icon@svg-small");

    essentialPluginIds.sort(null);
  }

  public static @NotNull ApplicationInfoEx getShadowInstance() {
    return getShadowInstanceImpl();
  }

  @ApiStatus.Internal
  public static @NotNull ApplicationInfoImpl getShadowInstanceImpl() {
    ApplicationInfoImpl result = instance;
    if (result != null) {
      return result;
    }

    //noinspection SynchronizeOnThis
    synchronized (ApplicationInfoImpl.class) {
      result = instance;
      if (result == null) {
        Activity activity = StartUpMeasurer.startActivity("app info loading");
        try {
          result = new ApplicationInfoImpl(ApplicationNamesInfo.initAndGetRawData());
          instance = result;
        }
        finally {
          activity.end();
        }
      }
    }
    return result;
  }

  public static @NotNull String orFromPluginCompatibleBuild(@Nullable BuildNumber buildNumber) {
    BuildNumber number = buildNumber == null ? getShadowInstanceImpl().getPluginCompatibleBuildAsNumber() : buildNumber;
    return number.asString();
  }

  @Override
  public Calendar getBuildDate() {
    return GregorianCalendar.from(getBuildTime());
  }

  @Override
  public @NotNull ZonedDateTime getBuildTime() {
    if (buildTime == null) {
      buildTime = ZonedDateTime.now();
    }
    return buildTime;
  }

  @Override
  public @NotNull Calendar getMajorReleaseBuildDate() {
    return majorReleaseBuildDate == null ? getBuildDate() : GregorianCalendar.from(majorReleaseBuildDate);
  }

  @TestOnly
  public @Nullable ZonedDateTime getMajorReleaseBuildDateTime() {
    return majorReleaseBuildDate;
  }

  @Override
  public @NotNull BuildNumber getBuild() {
    return requireNonNull(BuildNumber.fromString(myBuildNumber));
  }

  @Override
  public @Nullable String getBuildBranchName() {
    return myBuildBranchName;
  }

  @Override
  public @NotNull String getApiVersion() {
    return getApiVersionAsNumber().asString();
  }

  @Override
  public @NotNull BuildNumber getApiVersionAsNumber() {
    BuildNumber build = getBuild();
    if (myApiVersion != null) {
      BuildNumber api = BuildNumber.fromStringWithProductCode(myApiVersion, build.getProductCode());
      if (api != null) return api;
    }
    return build;
  }

  @Override
  public String getMajorVersion() {
    return myMajorVersion;
  }

  @Override
  public String getMinorVersion() {
    return myMinorVersion;
  }

  @Override
  public String getMicroVersion() {
    return myMicroVersion;
  }

  @Override
  public String getPatchVersion() {
    return myPatchVersion;
  }

  @TestOnly
  public @Nullable String getVersionSuffix() {
    return myVersionSuffix;
  }

  @Override
  public @NotNull String getFullVersion() {
    String result;
    if (myFullVersionFormat != null) {
      result = MessageFormat.format(myFullVersionFormat, myMajorVersion, myMinorVersion, myMicroVersion, myPatchVersion);
    }
    else {
      result = requireNonNullElse(myMajorVersion) + '.' + requireNonNullElse(myMinorVersion);
    }
    if (myVersionSuffix != null && !myVersionSuffix.isEmpty()) {
      result += " " + myVersionSuffix;
    }
    return result;
  }

  @Override
  public @NotNull String getStrictVersion() {
    return myMajorVersion + "." + myMinorVersion + "." + requireNonNullElse(myMicroVersion) + "." + requireNonNullElse(myPatchVersion);
  }

  @Override
  public String getVersionName() {
    String fullName = ApplicationNamesInfo.getInstance().getFullProductName();
    if (isEap && myCodeName != null && !myCodeName.isEmpty()) {
      fullName += " (" + myCodeName + ")";
    }
    return fullName;
  }

  @Override
  public String getShortCompanyName() {
    return myShortCompanyName;
  }

  @Override
  public String getCompanyName() {
    return myCompanyName;
  }

  @Override
  public String getCompanyURL() {
    return IdeUrlTrackingParametersProvider.getInstance().augmentUrl(myCompanyUrl);
  }

  @Override
  public @Nullable String getSplashImageUrl() {
    if (isEap && eapSplashImageUrl != null) return eapSplashImageUrl;

    if (simplifiedSplashImageUrl != null) {
      Path markerFile = PathManager.getConfigDir().resolve(SIMPLIFIED_SPLASH_MARKER_FILE_NAME);
      if (Files.exists(markerFile)) {
        return simplifiedSplashImageUrl;
      }
    }

    String imageUrl = splashImageUrl;
    if (subscriptionModeSplashImageUrl != null) {
      Path markerFile = PathManager.getConfigDir().resolve(SUBSCRIPTION_MODE_SPLASH_MARKER_FILE_NAME);
      if (Files.exists(markerFile)) {
        imageUrl = subscriptionModeSplashImageUrl;
      }
    }

    String specialAnniversarySplash = specialAnniversarySplash(imageUrl);
    return specialAnniversarySplash != null ? specialAnniversarySplash : imageUrl;
  }

  private @Nullable String specialAnniversarySplash(String imageUrl) {
    if (getVersionName().equals("IntelliJ IDEA")) {
      LocalDate startDate = LocalDate.of(2026, Month.JULY, 5);
      LocalDate endDate = LocalDate.of(2026, Month.JULY, 13);
      LocalDate nowDate = LocalDate.now();
      if (imageUrl != null &&
          (
            Boolean.parseBoolean(System.getProperty("show.kotlin.anniversary.splash")) ||
            nowDate.isAfter(startDate) && nowDate.isBefore(endDate)
          )
      ) {
        return imageUrl.replace(".png", "_kotlin_15.png");
      }
    }
    return null;
  }

  @Override
  public @NotNull String getApplicationSvgIconUrl() {
    return getApplicationSvgIconUrl(isEAP());
  }

  @ApiStatus.Internal
  public @NotNull String getApplicationSvgIconUrl(boolean isEap) {
    return isEap && mySvgEapIconUrl != null ? mySvgEapIconUrl : svgIconUrl;
  }

  @Override
  public @NotNull String getSmallApplicationSvgIconUrl() {
    return getSmallApplicationSvgIconUrl(isEAP());
  }

  @ApiStatus.Internal
  public @NotNull String getSmallApplicationSvgIconUrl(boolean isEap) {
    return isEap && mySmallSvgEapIconUrl != null ? mySmallSvgEapIconUrl : mySmallSvgIconUrl;
  }

  @Override
  public boolean isEAP() {
    return isEap;
  }

  @Override
  public boolean isMajorEAP() {
    return isEap && (myMinorVersion == null || myMinorVersion.indexOf('.') < 0);
  }

  @Override
  public boolean isPreview() {
    return !isEap && myVersionSuffix != null && ("Preview".equalsIgnoreCase(myVersionSuffix) || myVersionSuffix.startsWith("RC"));
  }

  @Override
  public @Nullable String getFullIdeProductCode() {
    return System.getProperty("intellij.platform.full.ide.product.code");
  }

  @Override
  public @NotNull String getPluginManagerUrl() {
    return myPluginManagerUrl;
  }

  @Override
  public boolean usesJetBrainsPluginRepository() {
    return DEFAULT_PLUGINS_HOST.equalsIgnoreCase(myPluginManagerUrl);
  }

  @Override
  public @NotNull String getPluginsListUrl() {
    return myPluginsListUrl;
  }

  @Override
  public @NotNull String getPluginDownloadUrl() {
    return pluginDownloadUrl;
  }

  @Override
  public String getFullApplicationName() {
    return getVersionName() + " " + getFullVersion();
  }

  @Override
  public String getCopyrightStart() {
    return myCopyrightStart;
  }

  @Override
  public String getSubscriptionFormId() {
    return mySubscriptionFormId;
  }

  @Override
  public boolean areSubscriptionTipsAvailable() {
    return mySubscriptionTipsAvailable;
  }

  public @NotNull @NlsSafe String getPluginCompatibleBuild() {
    return getPluginCompatibleBuildAsNumber().asString();
  }

  public @NotNull BuildNumber getPluginCompatibleBuildAsNumber() {
    BuildNumber compatibleBuild = BuildNumber.fromPluginCompatibleBuild();
    if (LOG.isDebugEnabled()) {
      LOG.debug("getPluginsCompatibleBuildAsNumber: compatibleBuild=" + (compatibleBuild == null ? "null" : compatibleBuild.asString()));
    }
    BuildNumber version = compatibleBuild == null ? getApiVersionAsNumber() : compatibleBuild;
    if (LOG.isDebugEnabled()) {
      LOG.debug("getPluginsCompatibleBuildAsNumber: version=" + version.asString());
    }
    BuildNumber buildNumber = BuildNumber.fromStringWithProductCode(version.asString(), getBuild().getProductCode());
    return requireNonNull(buildNumber);
  }

  private static @Nullable String getAttributeValue(XmlElement element, String name) {
    String value = element.getAttributeValue(name);
    return (value == null || value.isEmpty()) ? null : value;
  }

  private void readBuildInfo(XmlElement element) {
    myBuildNumber = getAttributeValue(element, "number");
    myApiVersion = getAttributeValue(element, "apiVersion");
    myBuildBranchName = getAttributeValue(element, "branchName");

    String dateString = element.getAttributeValue("date");
    if (dateString != null && !dateString.equals("__BUILD_DATE__")) {
      buildTime = parseDate(dateString);
    }

    String majorReleaseDateString = element.getAttributeValue("majorReleaseDate");
    if (majorReleaseDateString != null) {
      majorReleaseBuildDate = parseDate(majorReleaseDateString);
    }
  }

  private void readPluginInfo(@Nullable XmlElement element) {
    String pluginManagerUrl = DEFAULT_PLUGINS_HOST;
    String pluginListUrl = null;
    pluginDownloadUrl = null;

    if (element != null) {
      String url = element.getAttributeValue("url");
      if (url != null) {
        pluginManagerUrl = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
      }

      String listUrl = element.getAttributeValue("list-url");
      if (listUrl != null) {
        pluginListUrl = listUrl;
      }

      String downloadUrl = element.getAttributeValue("download-url");
      if (downloadUrl != null) {
        pluginDownloadUrl = downloadUrl;
      }
    }

    String pluginHost = System.getProperty(IDEA_PLUGINS_HOST_PROPERTY);
    if (pluginHost != null) {
      pluginManagerUrl = pluginHost.endsWith("/") ? pluginHost.substring(0, pluginHost.length() - 1) : pluginHost;
      pluginListUrl = pluginDownloadUrl = null;
    }

    myPluginManagerUrl = pluginManagerUrl;
    myPluginsListUrl = pluginListUrl == null ? (pluginManagerUrl + "/plugins/list/") : pluginListUrl;
    if (pluginDownloadUrl == null) {
      pluginDownloadUrl = pluginManagerUrl + "/pluginManager/";
    }
  }

  // copy of ApplicationInfoProperties.shortenCompanyName
  private static String shortenCompanyName(String name) {
    if (name.endsWith(" s.r.o.")) name = name.substring(0, name.length() - " s.r.o.".length());
    if (name.endsWith(" Inc.")) name = name.substring(0, name.length() - " Inc.".length());
    return name;
  }

  private static @Nullable ZonedDateTime parseDate(@NotNull String dateString) {
    try {
      int year = Integer.parseInt(dateString.substring(0, 4));
      // 0-based for old GregorianCalendar and 1-based for ZonedDateTime
      int month = Integer.parseInt(dateString.substring(4, 6));
      int dayOfMonth = Integer.parseInt(dateString.substring(6, 8));
      int hour;
      int minute;
      if (dateString.length() > 8) {
        hour = Integer.parseInt(dateString.substring(8, 10));
        minute = Integer.parseInt(dateString.substring(10, 12));
      }
      else {
        hour = 0;
        minute = 0;
      }
      return ZonedDateTime.of(year, month, dayOfMonth, hour, minute, 0, 0, ZoneOffset.UTC);
    }
    catch (Exception ignore) {
      return null;
    }
  }

  @ReviseWhenPortedToJDK("9")
  private static String requireNonNullElse(String s) {
    return s != null ? s : "0";
  }

  @Override
  public boolean isEssentialPlugin(@NotNull String pluginId) {
    return PluginManagerCore.CORE_PLUGIN_ID.equals(pluginId) || isEssentialPlugin(PluginId.getId(pluginId));
  }

  @Override
  public boolean isEssentialPlugin(@NotNull PluginId pluginId) {
    return PluginManagerCore.CORE_ID.equals(pluginId) || Collections.binarySearch(essentialPluginIds, pluginId) >= 0;
  }

  @Override
  public @NotNull List<PluginId> getEssentialPluginIds() {
    return essentialPluginIds;
  }

  @Override
  public @Nullable String getDefaultLightLaf() {
    String override = System.getProperty(IDEA_APPLICATION_INFO_DEFAULT_LIGHT_LAF);
    return override != null ? override : myDefaultLightLaf;
  }

  @Override
  public @Nullable String getDefaultClassicLightLaf() {
    String override = System.getProperty(IDEA_APPLICATION_INFO_DEFAULT_CLASSIC_LIGHT_LAF);
    return override != null ? override : myDefaultClassicLightLaf;
  }

  @Override
  public @Nullable String getDefaultDarkLaf() {
    String override = System.getProperty(IDEA_APPLICATION_INFO_DEFAULT_DARK_LAF);
    return override != null ? override : myDefaultDarkLaf;
  }

  @Override
  public @Nullable String getDefaultClassicDarkLaf() {
    String override = System.getProperty(IDEA_APPLICATION_INFO_DEFAULT_CLASSIC_DARK_LAF);
    return override != null ? override : myDefaultClassicDarkLaf;
  }

  @Override
  public boolean isSimplifiedSplashSupported() {
    return simplifiedSplashImageUrl != null;
  }

  /** @deprecated Use {@link ApplicationManagerEx#isInStressTest} */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public static boolean isInStressTest() {
    return ApplicationManagerEx.isInStressTest();
  }
}
