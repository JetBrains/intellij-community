// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl;

import com.intellij.ReviseWhenPortedToJDK;
import com.intellij.diagnostic.Activity;
import com.intellij.diagnostic.ActivityCategory;
import com.intellij.diagnostic.StartUpMeasurer;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.IdeUrlTrackingParametersProvider;
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

import java.text.MessageFormat;
import java.util.*;

import static java.util.Objects.requireNonNull;

/**
 * Provides access to content of *ApplicationInfo.xml file. Scheme for *ApplicationInfo.xml files is defined
 * in platform/platform-resources/src/idea/ApplicationInfo.xsd,
 * so you need to update it when adding or removing support for some XML elements in this class.
 */
public final class ApplicationInfoImpl extends ApplicationInfoEx {
  public static final String DEFAULT_PLUGINS_HOST = "https://plugins.jetbrains.com";
         static final String IDEA_PLUGINS_HOST_PROPERTY = "idea.plugins.host";

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
  private String myApiVersion;
  private String myVersionSuffix;
  private String myCompanyName = "JetBrains s.r.o.";
  private String myCopyrightStart = "2000";
  private String myShortCompanyName;
  private String myCompanyUrl = "https://www.jetbrains.com/";
  private long myCopyrightForeground = -1;
  private long myAboutForeground = -1;
  private long myAboutLinkColor = -1;
  private int[] myAboutLogoRect;  // don't use Rectangle to avoid dependency on AWT
  private String mySplashImageUrl;
  private String myEapSplashImageUrl;
  private String myAboutImageUrl;
  private String mySmallIconUrl = "/icon_small.png";
  private String mySvgIconUrl;
  private String mySvgEapIconUrl;
  private String mySmallSvgIconUrl;
  private String mySmallSvgEapIconUrl;
  private String myToolWindowIconUrl = "/toolwindows/toolWindowProject.svg";
  private String myWelcomeScreenLogoUrl;

  private Calendar myBuildDate;
  private Calendar myMajorReleaseBuildDate;
  private boolean myShowLicensee = true;
  private String myWelcomeScreenDialog;
  private UpdateUrls myUpdateUrls;
  private String myDocumentationUrl;
  private String mySupportUrl;
  private String myYoutrackUrl;
  private String myFeedbackUrl;
  private String myPluginManagerUrl;
  private String myPluginsListUrl;
  private String myChannelsListUrl;
  private String myPluginsDownloadUrl;
  private String myBuiltinPluginsUrl;
  private String myWhatsNewUrl;
  private boolean myShowWhatsNewOnUpdate;
  private String myWinKeymapUrl;
  private String myMacKeymapUrl;
  private boolean myEAP;
  private boolean myHasHelp = true;
  private boolean myHasContextHelp = true;
  private String myWebHelpUrl = "https://www.jetbrains.com/idea/webhelp/";
  private final List<PluginId> essentialPluginsIds = new ArrayList<>();
  private String myEventLogSettingsUrl = "https://resources.jetbrains.com/storage/fus/config/v4/%s/%s.json";
  private String myJetBrainsTvUrl;
  private String myKeyConversionUrl = "https://www.jetbrains.com/shop/eform/keys-exchange";

  private String mySubscriptionFormId;
  private String mySubscriptionNewsKey;
  private String mySubscriptionNewsValue;
  private String mySubscriptionTipsKey;
  private boolean mySubscriptionTipsAvailable;
  private String mySubscriptionAdditionalFormData;
  private XmlElement myFeedbackForm;

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
  ApplicationInfoImpl(@NotNull XmlElement element) {
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
          myEAP = Boolean.parseBoolean(child.getAttributeValue("eap"));
          myVersionSuffix = child.getAttributeValue("suffix", myEAP ? "EAP" : null);
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
          mySplashImageUrl = getAttributeValue(child, "url");
        }
        break;

        case "logo-eap": {
          myEapSplashImageUrl = getAttributeValue(child, "url");
        }
        break;

        case "about": {
          myAboutImageUrl = child.getAttributeValue("url");

          String v = child.getAttributeValue("foreground");
          if (v != null) {
            myAboutForeground = parseColor(v);
          }
          v = child.getAttributeValue("copyrightForeground");
          if (v != null) {
            myCopyrightForeground = parseColor(v);
          }

          String c = child.getAttributeValue("linkColor");
          if (c != null) {
            myAboutLinkColor = parseColor(c);
          }

          String logoX = child.getAttributeValue("logoX");
          String logoY = child.getAttributeValue("logoY");
          String logoW = child.getAttributeValue("logoW");
          String logoH = child.getAttributeValue("logoH");
          if (logoX != null && logoY != null && logoW != null && logoH != null) {
            try {
              myAboutLogoRect = new int[]{Integer.parseInt(logoX), Integer.parseInt(logoY), Integer.parseInt(logoW), Integer.parseInt(logoH)};
            }
            catch (NumberFormatException ignored) {
            }
          }
        }
        break;

        case "icon": {
          mySmallIconUrl = child.getAttributeValue("size16", mySmallIconUrl);
          String toolWindowIcon = getAttributeValue(child, "size12");
          if (toolWindowIcon != null) {
            myToolWindowIconUrl = toolWindowIcon;
          }
          mySvgIconUrl = child.getAttributeValue("svg");
          mySmallSvgIconUrl = child.getAttributeValue("svg-small");
        }
        break;

        case "icon-eap": {
          mySvgEapIconUrl = child.getAttributeValue("svg");
          mySmallSvgEapIconUrl = child.getAttributeValue("svg-small");
        }
        break;

        case "licensee": {
          myShowLicensee = Boolean.parseBoolean(child.getAttributeValue("show"));
        }
        break;

        case "welcome-screen": {
          myWelcomeScreenLogoUrl = child.getAttributeValue("logo-url");
        }
        break;

        case "welcome-wizard": {
          myWelcomeScreenDialog = getAttributeValue(child, "dialog");
        }
        break;

        case "help": {
          String webHelpUrl = getAttributeValue(child, "webhelp-url");
          if (webHelpUrl != null) {
            myWebHelpUrl = webHelpUrl;
          }

          String attValue = child.getAttributeValue("has-help");
          myHasHelp = attValue == null || Boolean.parseBoolean(attValue); // Default is true

          attValue = child.getAttributeValue("has-context-help");
          myHasContextHelp = attValue == null || Boolean.parseBoolean(attValue); // Default is true
        }
        break;

        case "update-urls": {
          myUpdateUrls = new UpdateUrlsImpl(child);
        }
        break;

        case "documentation": {
          myDocumentationUrl = child.getAttributeValue("url");
        }
        break;

        case "support": {
          mySupportUrl = child.getAttributeValue("url");
        }
        break;

        case "youtrack": {
          myYoutrackUrl = child.getAttributeValue("url");
        }
        break;

        case "feedback": {
          myFeedbackUrl = child.getAttributeValue("url");
          if (child.getAttributeValue("zendesk-form-id") != null) {
            myFeedbackForm = child;
          }
        }
        break;

        //noinspection SpellCheckingInspection
        case "whatsnew": {
          myWhatsNewUrl = child.getAttributeValue("url");
          myShowWhatsNewOnUpdate = Boolean.parseBoolean(child.getAttributeValue("show-on-update"));
        }
        break;

        case "plugins": {
          readPluginInfo(child);
        }
        break;

        case "keymap": {
          myWinKeymapUrl = child.getAttributeValue("win");
          myMacKeymapUrl = child.getAttributeValue("mac");
        }
        break;

        case "essential-plugin": {
          String id = child.content;
          if (id != null && !id.isEmpty()) {
            essentialPluginsIds.add(PluginId.getId(id));
          }
        }
        break;

        case "statistics": {
          myEventLogSettingsUrl = child.getAttributeValue("event-log-settings");
        }
        break;

        case "jetbrains-tv": {
          myJetBrainsTvUrl = child.getAttributeValue("url");
        }
        break;

        case "licensing": {
          String url = getAttributeValue(child, "key-conversion-url");
          if (url != null) {
            myKeyConversionUrl = url.trim();
          }
        }
        break;

        case "subscriptions": {
          //noinspection SpellCheckingInspection
          mySubscriptionFormId = child.getAttributeValue("formid");
          mySubscriptionNewsKey = child.getAttributeValue("news-key");
          mySubscriptionNewsValue = child.getAttributeValue("news-value", "yes");
          mySubscriptionTipsKey = child.getAttributeValue("tips-key");
          mySubscriptionTipsAvailable = Boolean.parseBoolean(child.getAttributeValue("tips-available"));
          mySubscriptionAdditionalFormData = child.getAttributeValue("additional-form-data");
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

    requireNonNull(mySvgIconUrl, "Missing attribute: //icon@svg");
    requireNonNull(mySmallSvgIconUrl, "Missing attribute: //icon@svg-small");

    overrideFromProperties();

    essentialPluginsIds.sort(null);
  }

  private void overrideFromProperties() {
    String youTrackUrlOverride = System.getProperty("application.info.youtrack.url");
    if (youTrackUrlOverride != null) {
      myYoutrackUrl = youTrackUrlOverride;
    }
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
        Activity activity = StartUpMeasurer.startActivity("app info loading", ActivityCategory.DEFAULT);
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

  public static @NotNull String orFromPluginsCompatibleBuild(@Nullable BuildNumber buildNumber) {
    BuildNumber number = buildNumber != null ? buildNumber : getShadowInstanceImpl().getPluginsCompatibleBuildAsNumber();
    return number.asString();
  }

  @Override
  public Calendar getBuildDate() {
    if (myBuildDate == null) {
      myBuildDate = Calendar.getInstance();
    }
    return myBuildDate;
  }

  @Override
  public Calendar getMajorReleaseBuildDate() {
    return myMajorReleaseBuildDate != null ? myMajorReleaseBuildDate : myBuildDate;
  }

  @Override
  public @NotNull BuildNumber getBuild() {
    return requireNonNull(BuildNumber.fromString(myBuildNumber));
  }

  @Override
  public @NotNull String getApiVersion() {
    return getApiVersionAsNumber().asString();
  }

  @Override
  public @NotNull BuildNumber getApiVersionAsNumber() {
    BuildNumber build = getBuild();
    if (LOG.isDebugEnabled()) {
      LOG.debug("getApiVersionAsNumber: build=" + build.asString());
    }
    if (myApiVersion != null) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("getApiVersionAsNumber: myApiVersion=" + build.asString());
      }
      BuildNumber api = BuildNumber.fromStringWithProductCode(myApiVersion, build.getProductCode());
      if (api != null) {
        return api;
      }
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
    if (myEAP && myCodeName != null && !myCodeName.isEmpty()) {
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
  public String getSplashImageUrl() {
    return isEAP() && myEapSplashImageUrl != null ? myEapSplashImageUrl : mySplashImageUrl;
  }

  @Override
  public String getAboutImageUrl() {
    return myAboutImageUrl;
  }

  @Override
  public long getCopyrightForeground() {
    return myCopyrightForeground;
  }

  @Override
  public @NotNull String getSmallIconUrl() {
    return mySmallIconUrl;
  }

  @Override
  public @NotNull String getApplicationSvgIconUrl() {
    return isEAP() && mySvgEapIconUrl != null ? mySvgEapIconUrl : mySvgIconUrl;
  }

  @Override
  public @NotNull String getSmallApplicationSvgIconUrl() {
    return getSmallApplicationSvgIconUrl(isEAP());
  }

  public @NotNull String getSmallApplicationSvgIconUrl(boolean isEap) {
    return isEap && mySmallSvgEapIconUrl != null ? mySmallSvgEapIconUrl : mySmallSvgIconUrl;
  }

  @Override
  public String getToolWindowIconUrl() {
    return myToolWindowIconUrl;
  }

  @Override
  public @Nullable String getWelcomeScreenLogoUrl() {
    return myWelcomeScreenLogoUrl;
  }

  @Override
  public @Nullable String getWelcomeWizardDialog() { return myWelcomeScreenDialog; }

  @Override
  public boolean isEAP() {
    return myEAP;
  }

  @Override
  public boolean isMajorEAP() {
    return myEAP && (myMinorVersion == null || myMinorVersion.indexOf('.') < 0);
  }

  @Override
  public boolean isPreview() {
    return !myEAP && myVersionSuffix != null && ("Preview".equalsIgnoreCase(myVersionSuffix) || myVersionSuffix.startsWith("RC"));
  }

  @Override
  public @Nullable UpdateUrls getUpdateUrls() {
    return myUpdateUrls;
  }

  @Override
  public String getDocumentationUrl() {
    return myDocumentationUrl;
  }

  @Override
  public String getSupportUrl() {
    return mySupportUrl;
  }

  @Override
  public String getYoutrackUrl() {
    return myYoutrackUrl;
  }

  @Override
  public String getFeedbackUrl() {
    return myFeedbackUrl;
  }

  @Override
  public String getPluginManagerUrl() {
    return myPluginManagerUrl;
  }

  @Override
  public boolean usesJetBrainsPluginRepository() {
    return DEFAULT_PLUGINS_HOST.equalsIgnoreCase(myPluginManagerUrl);
  }

  @Override
  public String getPluginsListUrl() {
    return myPluginsListUrl;
  }

  @Override
  public String getChannelsListUrl() {
    return myChannelsListUrl;
  }

  @Override
  public String getPluginsDownloadUrl() {
    return myPluginsDownloadUrl;
  }

  @Override
  public String getBuiltinPluginsUrl() {
    return myBuiltinPluginsUrl;
  }

  @Override
  public String getWebHelpUrl() {
    return myWebHelpUrl;
  }

  @Override
  public boolean hasHelp() {
    return myHasHelp;
  }

  @Override
  public boolean hasContextHelp() {
    return myHasContextHelp;
  }

  @Override
  public String getWhatsNewUrl() {
    return myWhatsNewUrl;
  }

  @Override
  public boolean isShowWhatsNewOnUpdate() {
    return myShowWhatsNewOnUpdate;
  }

  @Override
  public String getWinKeymapUrl() {
    return myWinKeymapUrl;
  }

  @Override
  public String getMacKeymapUrl() {
    return myMacKeymapUrl;
  }

  @Override
  public long getAboutForeground() {
    return myAboutForeground;
  }

  @Override
  public long getAboutLinkColor() {
    return myAboutLinkColor;
  }

  @Override
  public String getFullApplicationName() {
    return getVersionName() + " " + getFullVersion();
  }

  @Override
  public boolean showLicenseeInfo() {
    return myShowLicensee;
  }

  @Override
  public String getCopyrightStart() {
    return myCopyrightStart;
  }

  public String getEventLogSettingsUrl() {
    return myEventLogSettingsUrl;
  }

  @Override
  public String getJetBrainsTvUrl() {
    return myJetBrainsTvUrl;
  }

  @Override
  public String getKeyConversionUrl() {
    return myKeyConversionUrl;
  }

  @Override
  public int @Nullable [] getAboutLogoRect() {
    return myAboutLogoRect;
  }

  @Override
  public String getSubscriptionFormId() {
    return mySubscriptionFormId;
  }

  @Override
  public String getSubscriptionNewsKey() {
    return mySubscriptionNewsKey;
  }

  @Override
  public String getSubscriptionNewsValue() {
    return mySubscriptionNewsValue;
  }

  @Override
  public String getSubscriptionTipsKey() {
    return mySubscriptionTipsKey;
  }

  @Override
  public boolean areSubscriptionTipsAvailable() {
    return mySubscriptionTipsAvailable;
  }

  @Override
  public @Nullable String getSubscriptionAdditionalFormData() {
    return mySubscriptionAdditionalFormData;
  }

  public @NotNull @NlsSafe String getPluginsCompatibleBuild() {
    return getPluginsCompatibleBuildAsNumber().asString();
  }

  public @NotNull BuildNumber getPluginsCompatibleBuildAsNumber() {
    BuildNumber compatibleBuild = BuildNumber.fromPluginsCompatibleBuild();
    if (LOG.isDebugEnabled()) {
      LOG.debug("getPluginsCompatibleBuildAsNumber: compatibleBuild=" + (compatibleBuild == null ? "null" : compatibleBuild.asString()));
    }
    BuildNumber version = compatibleBuild != null ? compatibleBuild : getApiVersionAsNumber();
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

    String dateString = element.getAttributeValue("date");
    if (dateString != null && !dateString.equals("__BUILD_DATE__")) {
      myBuildDate = parseDate(dateString);
    }

    String majorReleaseDateString = element.getAttributeValue("majorReleaseDate");
    if (majorReleaseDateString != null) {
      myMajorReleaseBuildDate = parseDate(majorReleaseDateString);
    }
  }

  private void readPluginInfo(@Nullable XmlElement element) {
    String pluginManagerUrl = DEFAULT_PLUGINS_HOST;
    String pluginsListUrl = null;
    myChannelsListUrl = null;
    myPluginsDownloadUrl = null;
    if (element != null) {
      String url = element.getAttributeValue("url");
      if (url != null) {
        pluginManagerUrl = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
      }

      String listUrl = element.getAttributeValue("list-url");
      if (listUrl != null) {
        pluginsListUrl = listUrl;
      }

      String channelListUrl = element.getAttributeValue("channel-list-url");
      if (channelListUrl != null) {
        myChannelsListUrl = channelListUrl;
      }

      String downloadUrl = element.getAttributeValue("download-url");
      if (downloadUrl != null) {
        myPluginsDownloadUrl = downloadUrl;
      }

      String builtinPluginsUrl = element.getAttributeValue("builtin-url");
      if (builtinPluginsUrl != null && !builtinPluginsUrl.isEmpty()) {
        myBuiltinPluginsUrl = builtinPluginsUrl;
      }
    }

    String pluginHost = System.getProperty(IDEA_PLUGINS_HOST_PROPERTY);
    if (pluginHost != null) {
      pluginManagerUrl = pluginHost.endsWith("/") ? pluginHost.substring(0, pluginHost.length() - 1) : pluginHost;
      pluginsListUrl = myChannelsListUrl = myPluginsDownloadUrl = null;
    }

    myPluginManagerUrl = pluginManagerUrl;
    myPluginsListUrl = pluginsListUrl == null ? (pluginManagerUrl + "/plugins/list/") : pluginsListUrl;
    if (myChannelsListUrl == null) {
      myChannelsListUrl = pluginManagerUrl + "/channels/list/";
    }
    if (myPluginsDownloadUrl == null) {
      myPluginsDownloadUrl = pluginManagerUrl + "/pluginManager/";
    }
  }

  // copy of ApplicationInfoProperties.shortenCompanyName
  @SuppressWarnings("SSBasedInspection")
  private static String shortenCompanyName(String name) {
    if (name.endsWith(" s.r.o.")) name = name.substring(0, name.length() - " s.r.o.".length());
    if (name.endsWith(" Inc.")) name = name.substring(0, name.length() - " Inc.".length());
    return name;
  }

  private static GregorianCalendar parseDate(String dateString) {
    GregorianCalendar calendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
    try {
      calendar.set(Calendar.YEAR, Integer.parseInt(dateString.substring(0, 4)));
      calendar.set(Calendar.MONTH, Integer.parseInt(dateString.substring(4, 6)) - 1);
      calendar.set(Calendar.DAY_OF_MONTH, Integer.parseInt(dateString.substring(6, 8)));
      if (dateString.length() > 8) {
        calendar.set(Calendar.HOUR_OF_DAY, Integer.parseInt(dateString.substring(8, 10)));
        calendar.set(Calendar.MINUTE, Integer.parseInt(dateString.substring(10, 12)));
      }
      else {
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
      }
    }
    catch (Exception ignore) { }
    return calendar;
  }

  private static long parseColor(String colorString) {
    return Long.parseLong(colorString, 16);
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
    return PluginManagerCore.CORE_ID.equals(pluginId) || Collections.binarySearch(essentialPluginsIds, pluginId) >= 0;
  }

  @Override
  public @NotNull List<PluginId> getEssentialPluginsIds() {
    return essentialPluginsIds;
  }

  @Override
  public @Nullable String getDefaultLightLaf() {
    String override = System.getProperty(IDEA_APPLICATION_INFO_DEFAULT_LIGHT_LAF);
    if (override != null) {
      return override;
    }
    return myDefaultLightLaf;
  }

  @Override
  public @Nullable String getDefaultClassicLightLaf() {
    String override = System.getProperty(IDEA_APPLICATION_INFO_DEFAULT_CLASSIC_LIGHT_LAF);
    if (override != null) {
      return override;
    }
    return myDefaultClassicLightLaf;
  }

  @Override
  public @Nullable String getDefaultDarkLaf() {
    String override = System.getProperty(IDEA_APPLICATION_INFO_DEFAULT_DARK_LAF);
    if (override != null) {
      return override;
    }
    return myDefaultDarkLaf;
  }

  @Override
  public @Nullable String getDefaultClassicDarkLaf() {
    String override = System.getProperty(IDEA_APPLICATION_INFO_DEFAULT_CLASSIC_DARK_LAF);
    if (override != null) {
      return override;
    }
    return myDefaultClassicDarkLaf;
  }

  public @Nullable ZenDeskForm getFeedbackForm() {
    XmlElement v = myFeedbackForm;
    return v == null ? null : ZenDeskForm.parse(v);
  }

  private static final class UpdateUrlsImpl implements UpdateUrls {
    private final String myCheckingUrl;
    private final String myPatchesUrl;

    private UpdateUrlsImpl(XmlElement element) {
      myCheckingUrl = element.getAttributeValue("check");
      myPatchesUrl = element.getAttributeValue("patches");
    }

    @Override
    public String getCheckingUrl() {
      return myCheckingUrl;
    }

    @Override
    public String getPatchesUrl() {
      return myPatchesUrl;
    }
  }

  /** @deprecated Use {@link ApplicationManagerEx#isInStressTest} */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public static boolean isInStressTest() {
    return ApplicationManagerEx.isInStressTest();
  }
}
