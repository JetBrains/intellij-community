// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.openapi.application.ex.ProgressSlide;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.serviceContainer.NonInjectable;
import org.jdom.Element;
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
  private long myProgressColor = -1;
  private long myCopyrightForeground = -1;
  private long myAboutForeground = -1;
  private long myAboutLinkColor = -1;
  private int[] myAboutLogoRect;  // don't use Rectangle to avoid dependency on AWT
  private String myProgressTailIconName;
  private int myProgressHeight = 2;
  private int myProgressY = 350;
  private String mySplashImageUrl;
  private String myAboutImageUrl;
  private String myIconUrl = "/icon.png";
  private String mySmallIconUrl = "/icon_small.png";
  private String myBigIconUrl;
  private String mySvgIconUrl;
  private String mySvgEapIconUrl;
  private String mySmallSvgIconUrl;
  private String mySmallSvgEapIconUrl;
  private String myToolWindowIconUrl = "/toolwindows/toolWindowProject.svg";
  private String myWelcomeScreenLogoUrl;

  private Calendar myBuildDate;
  private Calendar myMajorReleaseBuildDate;
  private String myPackageCode;
  private boolean myShowLicensee = true;
  private String myCustomizeIDEWizardStepsProvider;
  private String myCustomizeIDEWizardDialog;
  private final UpdateUrls myUpdateUrls;
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
  private int myWhatsNewEligibility;
  private String myWinKeymapUrl;
  private String myMacKeymapUrl;
  private boolean myEAP;
  private boolean myHasHelp = true;
  private boolean myHasContextHelp = true;
  private String myWebHelpUrl = "https://www.jetbrains.com/idea/webhelp/";
  private final List<PluginId> myEssentialPluginsIds;
  private final String myEventLogSettingsUrl;
  private String myJetBrainsTvUrl;
  private String myEvalLicenseUrl = "https://www.jetbrains.com/store/license.html";
  private String myKeyConversionUrl = "https://www.jetbrains.com/shop/eform/keys-exchange";

  private String mySubscriptionFormId;
  private String mySubscriptionNewsKey;
  private String mySubscriptionNewsValue;
  private String mySubscriptionTipsKey;
  private boolean mySubscriptionTipsAvailable;
  private String mySubscriptionAdditionalFormData;
  private final List<ProgressSlide> myProgressSlides = new ArrayList<>();

  private String myDefaultLightLaf;
  private String myDefaultDarkLaf;

  public static final String DEFAULT_PLUGINS_HOST = "https://plugins.jetbrains.com";
  static final String IDEA_PLUGINS_HOST_PROPERTY = "idea.plugins.host";

  private static volatile ApplicationInfoImpl instance;

  // if application loader was not used
  @SuppressWarnings("unused")
  private ApplicationInfoImpl() {
    this(ApplicationNamesInfo.initAndGetRawData());
  }

  @NonInjectable
  ApplicationInfoImpl(@NotNull Element element) {
    // behavior of this method must be consistent with idea/ApplicationInfo.xsd schema.
    Element versionElement = getChild(element, "version");
    if (versionElement != null) {
      myMajorVersion = versionElement.getAttributeValue("major");
      myMinorVersion = versionElement.getAttributeValue("minor");
      myMicroVersion = versionElement.getAttributeValue("micro");
      myPatchVersion = versionElement.getAttributeValue("patch");
      myFullVersionFormat = versionElement.getAttributeValue("full");
      myCodeName = versionElement.getAttributeValue("codename");
      myEAP = Boolean.parseBoolean(versionElement.getAttributeValue("eap"));
      myVersionSuffix = versionElement.getAttributeValue("suffix");
      if (myVersionSuffix == null && myEAP) {
        myVersionSuffix = "EAP";
      }
    }

    Element companyElement = getChild(element, "company");
    if (companyElement != null) {
      myCompanyName = companyElement.getAttributeValue("name", myCompanyName);
      myShortCompanyName = companyElement.getAttributeValue("shortName", shortenCompanyName(myCompanyName));
      myCompanyUrl = companyElement.getAttributeValue("url", myCompanyUrl);
      myCopyrightStart = companyElement.getAttributeValue("copyrightStart", myCopyrightStart);
    }

    Element buildElement = getChild(element, "build");
    if (buildElement != null) {
      readBuildInfo(buildElement);
    }

    Element logoElement = getChild(element, "logo");
    if (logoElement != null) {
      readLogoInfo(logoElement);
    }

    Element aboutLogoElement = getChild(element, "about");
    if (aboutLogoElement != null) {
      myAboutImageUrl = aboutLogoElement.getAttributeValue("url");

      String v = aboutLogoElement.getAttributeValue("foreground");
      if (v != null) {
        myAboutForeground = parseColor(v);
      }
      v = aboutLogoElement.getAttributeValue("copyrightForeground");
      if (v != null) {
        myCopyrightForeground = parseColor(v);
      }

      String c = aboutLogoElement.getAttributeValue("linkColor");
      if (c != null) {
        myAboutLinkColor = parseColor(c);
      }

      String logoX = aboutLogoElement.getAttributeValue("logoX");
      String logoY = aboutLogoElement.getAttributeValue("logoY");
      String logoW = aboutLogoElement.getAttributeValue("logoW");
      String logoH = aboutLogoElement.getAttributeValue("logoH");
      if (logoX != null && logoY != null && logoW != null && logoH != null) {
        try {
          myAboutLogoRect = new int[]{Integer.parseInt(logoX), Integer.parseInt(logoY), Integer.parseInt(logoW), Integer.parseInt(logoH)};
        }
        catch (NumberFormatException ignored) { }
      }
    }

    Element iconElement = getChild(element, "icon");
    if (iconElement != null) {
      myIconUrl = iconElement.getAttributeValue("size32");
      mySmallIconUrl = iconElement.getAttributeValue("size16", mySmallIconUrl);
      myBigIconUrl = getAttributeValue(iconElement, "size128");
      String toolWindowIcon = getAttributeValue(iconElement, "size12");
      if (toolWindowIcon != null) {
        myToolWindowIconUrl = toolWindowIcon;
      }
      mySvgIconUrl = iconElement.getAttributeValue("svg");
      mySmallSvgIconUrl = iconElement.getAttributeValue("svg-small");
    }
    Element iconEap = getChild(element, "icon-eap");
    if (iconEap != null) {
      mySvgEapIconUrl = iconEap.getAttributeValue("svg");
      mySmallSvgEapIconUrl = iconEap.getAttributeValue("svg-small");
    }

    Element packageElement = getChild(element, "package");
    if (packageElement != null) {
      myPackageCode = packageElement.getAttributeValue("code");
    }

    Element showLicensee = getChild(element, "licensee");
    if (showLicensee != null) {
      myShowLicensee = Boolean.parseBoolean(showLicensee.getAttributeValue("show"));
    }

    Element welcomeScreen = getChild(element, "welcome-screen");
    if (welcomeScreen != null) {
      myWelcomeScreenLogoUrl = welcomeScreen.getAttributeValue("logo-url");
    }

    Element wizardSteps = getChild(element, "customize-ide-wizard");
    if (wizardSteps != null) {
      myCustomizeIDEWizardStepsProvider = wizardSteps.getAttributeValue("provider");
      myCustomizeIDEWizardDialog = getAttributeValue(wizardSteps, "dialog");
    }

    Element helpElement = getChild(element, "help");
    if (helpElement != null) {
      String webHelpUrl = getAttributeValue(helpElement, "webhelp-url");
      if (webHelpUrl != null) {
        myWebHelpUrl = webHelpUrl;
      }

      String attValue = helpElement.getAttributeValue("has-help");
      myHasHelp = attValue == null || Boolean.parseBoolean(attValue); // Default is true

      attValue = helpElement.getAttributeValue("has-context-help");
      myHasContextHelp = attValue == null || Boolean.parseBoolean(attValue); // Default is true
    }

    Element updateUrls = getChild(element, "update-urls");
    myUpdateUrls = updateUrls == null ? null : new UpdateUrlsImpl(updateUrls);

    Element documentationElement = getChild(element, "documentation");
    if (documentationElement != null) {
      myDocumentationUrl = documentationElement.getAttributeValue("url");
    }

    Element supportElement = getChild(element, "support");
    if (supportElement != null) {
      mySupportUrl = supportElement.getAttributeValue("url");
    }

    Element youtrackElement = getChild(element, "youtrack");
    if (youtrackElement != null) {
      myYoutrackUrl = youtrackElement.getAttributeValue("url");
    }

    Element feedbackElement = getChild(element, "feedback");
    if (feedbackElement != null) {
      myFeedbackUrl = feedbackElement.getAttributeValue("url");
    }

    Element whatsNewElement = getChild(element, "whatsnew");
    if (whatsNewElement != null) {
      myWhatsNewUrl = whatsNewElement.getAttributeValue("url");
      String eligibility = whatsNewElement.getAttributeValue("eligibility");
      if ("embed".equals(eligibility)) myWhatsNewEligibility = WHATS_NEW_EMBED;
      else if ("auto".equals(eligibility)) myWhatsNewEligibility = WHATS_NEW_AUTO;
    }

    readPluginInfo(getChild(element, "plugins"));

    Element keymapElement = getChild(element, "keymap");
    if (keymapElement != null) {
      myWinKeymapUrl = keymapElement.getAttributeValue("win");
      myMacKeymapUrl = keymapElement.getAttributeValue("mac");
    }

    List<Element> essentialPluginsElements = getChildren(element, "essential-plugin");
    if (essentialPluginsElements.isEmpty()) {
      myEssentialPluginsIds = Collections.emptyList();
    }
    else {
      List<PluginId> essentialPluginsIds = new ArrayList<>(essentialPluginsElements.size());
      for (Element element1 : essentialPluginsElements) {
        String id = element1.getTextTrim();
        if (!id.isEmpty()) {
          essentialPluginsIds.add(PluginId.getId(id));
        }
      }
      essentialPluginsIds.sort(null);
      myEssentialPluginsIds = Collections.unmodifiableList(essentialPluginsIds);
    }

    Element statisticsElement = getChild(element, "statistics");
    if (statisticsElement != null) {
      myEventLogSettingsUrl = statisticsElement.getAttributeValue("event-log-settings");
    }
    else {
      myEventLogSettingsUrl = "https://resources.jetbrains.com/storage/fus/config/v4/%s/%s.json";
    }

    Element tvElement = getChild(element, "jetbrains-tv");
    if (tvElement != null) {
      myJetBrainsTvUrl = tvElement.getAttributeValue("url");
    }

    Element evaluationElement = getChild(element, "evaluation");
    if (evaluationElement != null) {
      String url = getAttributeValue(evaluationElement, "license-url");
      if (url != null) {
        myEvalLicenseUrl = url.trim();
      }
    }

    Element licensingElement = getChild(element, "licensing");
    if (licensingElement != null) {
      String url = getAttributeValue(licensingElement, "key-conversion-url");
      if (url != null) {
        myKeyConversionUrl = url.trim();
      }
    }

    Element subscriptionsElement = getChild(element, "subscriptions");
    if (subscriptionsElement != null) {
      mySubscriptionFormId = subscriptionsElement.getAttributeValue("formid");
      mySubscriptionNewsKey = subscriptionsElement.getAttributeValue("news-key");
      mySubscriptionNewsValue = subscriptionsElement.getAttributeValue("news-value", "yes");
      mySubscriptionTipsKey = subscriptionsElement.getAttributeValue("tips-key");
      mySubscriptionTipsAvailable = Boolean.parseBoolean(subscriptionsElement.getAttributeValue("tips-available"));
      mySubscriptionAdditionalFormData = subscriptionsElement.getAttributeValue("additional-form-data");
    }

    Element defaultLafElement = getChild(element, "default-laf");
    if (defaultLafElement != null) {
      String laf = getAttributeValue(defaultLafElement, "light");
      if (laf != null) {
        myDefaultLightLaf = laf.trim();
      }

      laf = getAttributeValue(defaultLafElement, "dark");
      if (laf != null) {
        myDefaultDarkLaf = laf.trim();
      }
    }
  }

  private void readLogoInfo(@NotNull Element element) {
    mySplashImageUrl = getAttributeValue(element, "url");
    String v = getAttributeValue(element, "progressColor");
    if (v != null) {
      myProgressColor = parseColor(v);
    }

    v = getAttributeValue(element, "progressTailIcon");
    if (v != null) {
      myProgressTailIconName = v;
    }

    v = getAttributeValue(element, "progressHeight");
    if (v != null) {
      myProgressHeight = Integer.parseInt(v);
    }

    v = getAttributeValue(element, "progressY");
    if (v != null) {
      myProgressY = Integer.parseInt(v);
    }

    for (Element child : getChildren(element, "progressSlide")) {
      String slideUrl = requireNonNull(child.getAttributeValue("url"));
      String progressPercent = requireNonNull(child.getAttributeValue("progressPercent"));
      int progressPercentInt = Integer.parseInt(progressPercent);
      if (progressPercentInt < 0 || progressPercentInt > 100) throw new IllegalArgumentException("Expected [0, 100], got " + progressPercent);
      float progressPercentFloat = (float)progressPercentInt / 100;
      myProgressSlides.add(new ProgressSlide(slideUrl, progressPercentFloat));
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
    if (myApiVersion != null) {
      BuildNumber api = fromStringWithProductCode(myApiVersion, build);
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
    return mySplashImageUrl;
  }

  @Override
  public String getAboutImageUrl() {
    return myAboutImageUrl;
  }

  @Override
  public long getProgressColor() {
    return myProgressColor;
  }

  @Override
  public long getCopyrightForeground() {
    return myCopyrightForeground;
  }

  @Override
  public int getProgressHeight() {
    return myProgressHeight;
  }

  @Override
  public int getProgressY() {
    return myProgressY;
  }

  @Override
  public @Nullable String getProgressTailIcon() {
    return myProgressTailIconName;
  }

  @Override
  public String getIconUrl() {
    return myIconUrl;
  }

  @Override
  public @NotNull String getSmallIconUrl() {
    return mySmallIconUrl;
  }

  @Override
  public @Nullable String getBigIconUrl() {
    return myBigIconUrl;
  }

  @Override
  public @Nullable String getApplicationSvgIconUrl() {
    return isEAP() && mySvgEapIconUrl != null ? mySvgEapIconUrl : mySvgIconUrl;
  }

  @Override
  public @Nullable String getSmallApplicationSvgIconUrl() {
    return getSmallApplicationSvgIconUrl(isEAP());
  }

  public @Nullable String getSmallApplicationSvgIconUrl(boolean isEap) {
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
  public @Nullable String getCustomizeIDEWizardDialog() { return myCustomizeIDEWizardDialog; }

  @Override
  public @Nullable String getCustomizeIDEWizardStepsProvider() {
    return myCustomizeIDEWizardStepsProvider;
  }

  @Override
  public String getPackageCode() {
    return myPackageCode;
  }

  @Override
  public boolean isEAP() {
    return myEAP;
  }

  @Override
  public boolean isMajorEAP() {
    return myEAP && (myMinorVersion == null || myMinorVersion.indexOf('.') < 0);
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
  public boolean isWhatsNewEligibleFor(int role) {
    return myWhatsNewEligibility >= role;
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
  public String getEvalLicenseUrl() {
    return myEvalLicenseUrl;
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

  @Override
  public @NotNull List<ProgressSlide> getProgressSlides() {
    return myProgressSlides;
  }

  public @NotNull @NlsSafe String getPluginsCompatibleBuild() {
    return getPluginsCompatibleBuildAsNumber().asString();
  }

  public @NotNull BuildNumber getPluginsCompatibleBuildAsNumber() {
    @Nullable BuildNumber compatibleBuild = BuildNumber.fromPluginsCompatibleBuild();
    BuildNumber version = compatibleBuild != null ? compatibleBuild : getApiVersionAsNumber();

    BuildNumber buildNumber = fromStringWithProductCode(version.asString(),
                                                        getBuild());
    return requireNonNull(buildNumber);
  }

  private static @Nullable BuildNumber fromStringWithProductCode(@NotNull String version, @NotNull BuildNumber buildNumber) {
    return BuildNumber.fromStringWithProductCode(version, buildNumber.getProductCode());
  }

  private static @Nullable String getAttributeValue(@NotNull Element element, @NotNull String name) {
    String value = element.getAttributeValue(name);
    return (value == null || value.isEmpty()) ? null : value;
  }

  private void readBuildInfo(@NotNull Element element) {
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

  private void readPluginInfo(@Nullable Element element) {
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

    String pluginsHost = System.getProperty(IDEA_PLUGINS_HOST_PROPERTY);
    if (pluginsHost != null) {
      pluginManagerUrl = pluginsHost.endsWith("/") ? pluginsHost.substring(0, pluginsHost.length() - 1) : pluginsHost;
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

  private static @NotNull List<Element> getChildren(@NotNull Element parentNode, @NotNull String name) {
    return parentNode.getChildren(name, parentNode.getNamespace());
  }

  private static Element getChild(@NotNull Element parentNode, @NotNull String name) {
    return parentNode.getChild(name, parentNode.getNamespace());
  }

  // copy of ApplicationInfoProperties.shortenCompanyName
  private static String shortenCompanyName(@NotNull String name) {
    if (name.endsWith(" s.r.o.")) {
      name = name.substring(0, name.length() - " s.r.o.".length());
    }
    if (name.endsWith(" Inc.")) {
      name = name.substring(0, name.length() - " Inc.".length());
    }
    return name;
  }

  private static @NotNull GregorianCalendar parseDate(@NotNull String dateString) {
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

  private static long parseColor(@NotNull String colorString) {
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
    return PluginManagerCore.CORE_ID.equals(pluginId) || Collections.binarySearch(myEssentialPluginsIds, pluginId) >= 0;
  }

  @Override
  public @NotNull List<PluginId> getEssentialPluginsIds() {
    return myEssentialPluginsIds;
  }

  @Override
  public @Nullable String getDefaultLightLaf() {
    return myDefaultLightLaf;
  }

  @Override
  public @Nullable String getDefaultDarkLaf() {
    return myDefaultDarkLaf;
  }

  private static final class UpdateUrlsImpl implements UpdateUrls {
    private final String myCheckingUrl;
    private final String myPatchesUrl;

    private UpdateUrlsImpl(@NotNull Element element) {
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
  public static boolean isInStressTest() {
    return ApplicationManagerEx.isInStressTest();
  }
}
