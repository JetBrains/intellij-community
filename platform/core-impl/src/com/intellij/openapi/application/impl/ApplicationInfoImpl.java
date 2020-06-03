// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application.impl;

import com.intellij.diagnostic.Activity;
import com.intellij.diagnostic.StartUpMeasurer;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.IdeUrlTrackingParametersProvider;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.application.ex.ProgressSlide;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.serviceContainer.NonInjectable;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.text.MessageFormat;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Provides access to content of *ApplicationInfo.xml file. Scheme for *ApplicationInfo.xml files is defined in platform/platform-resources/src/idea/ApplicationInfo.xsd,
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
  // don't use Rectangle to avoid dependency on awt
  private int[] myAboutLogoRect;
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
  private String myToolWindowIconUrl = "/toolwindows/toolWindowProject.png";
  private String myWelcomeScreenLogoUrl;

  private Calendar myBuildDate;
  private Calendar myMajorReleaseBuildDate;
  private String myPackageCode;
  private boolean myShowLicensee = true;
  private String myCustomizeIDEWizardStepsProvider;
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

  private static final String ELEMENT_VERSION = "version";
  private static final String ATTRIBUTE_MAJOR = "major";
  private static final String ATTRIBUTE_MINOR = "minor";
  private static final String ATTRIBUTE_MICRO = "micro";
  private static final String ATTRIBUTE_PATCH = "patch";
  private static final String ATTRIBUTE_FULL = "full";
  private static final String ATTRIBUTE_CODENAME = "codename";
  private static final String ATTRIBUTE_NAME = "name";
  private static final String ELEMENT_BUILD = "build";
  private static final String ELEMENT_COMPANY = "company";
  private static final String ATTRIBUTE_NUMBER = "number";
  private static final String ATTRIBUTE_API_VERSION = "apiVersion";
  private static final String ATTRIBUTE_DATE = "date";
  private static final String ATTRIBUTE_MAJOR_RELEASE_DATE = "majorReleaseDate";
  private static final String ELEMENT_LOGO = "logo";
  private static final String ATTRIBUTE_URL = "url";
  private static final String COPYRIGHT_START = "copyrightStart";
  private static final String ATTRIBUTE_PROGRESS_COLOR = "progressColor";
  private static final String ATTRIBUTE_ABOUT_FOREGROUND_COLOR = "foreground";
  private static final String ATTRIBUTE_ABOUT_COPYRIGHT_FOREGROUND_COLOR = "copyrightForeground";
  private static final String ATTRIBUTE_ABOUT_LINK_COLOR = "linkColor";
  private static final String ATTRIBUTE_PROGRESS_HEIGHT = "progressHeight";
  private static final String ATTRIBUTE_PROGRESS_Y = "progressY";
  private static final String ATTRIBUTE_PROGRESS_TAIL_ICON = "progressTailIcon";
  private static final String ELEMENT_ABOUT = "about";
  private static final String ELEMENT_ICON = "icon";
  private static final String ATTRIBUTE_SIZE16 = "size16";
  private static final String ATTRIBUTE_SIZE12 = "size12";
  private static final String ELEMENT_PACKAGE = "package";
  private static final String ATTRIBUTE_CODE = "code";
  private static final String ELEMENT_LICENSEE = "licensee";
  private static final String ATTRIBUTE_SHOW = "show";
  private static final String WELCOME_SCREEN_ELEMENT_NAME = "welcome-screen";
  private static final String LOGO_URL_ATTR = "logo-url";
  private static final String UPDATE_URLS_ELEMENT_NAME = "update-urls";
  private static final String ATTRIBUTE_EAP = "eap";
  private static final String HELP_ELEMENT_NAME = "help";
  private static final String ELEMENT_DOCUMENTATION = "documentation";
  private static final String ELEMENT_SUPPORT = "support";
  private static final String ELEMENT_YOUTRACK = "youtrack";
  private static final String ELEMENT_FEEDBACK = "feedback";
  private static final String ELEMENT_PLUGINS = "plugins";
  private static final String ATTRIBUTE_LIST_URL = "list-url";
  private static final String ATTRIBUTE_CHANNEL_LIST_URL = "channel-list-url";
  private static final String ATTRIBUTE_DOWNLOAD_URL = "download-url";
  private static final String ATTRIBUTE_BUILTIN_URL = "builtin-url";
  @SuppressWarnings("SpellCheckingInspection") private static final String ATTRIBUTE_WEBHELP_URL = "webhelp-url";
  private static final String ATTRIBUTE_HAS_HELP = "has-help";
  private static final String ATTRIBUTE_HAS_CONTEXT_HELP = "has-context-help";
  @SuppressWarnings("SpellCheckingInspection") private static final String ELEMENT_WHATS_NEW = "whatsnew";
  private static final String ELEMENT_KEYMAP = "keymap";
  private static final String ATTRIBUTE_WINDOWS_URL = "win";
  private static final String ATTRIBUTE_MAC_URL = "mac";
  private static final String ELEMENT_STATISTICS = "statistics";
  private static final String ATTRIBUTE_EVENT_LOG_STATISTICS_SETTINGS = "event-log-settings";
  private static final String ELEMENT_JB_TV = "jetbrains-tv";
  private static final String CUSTOMIZE_IDE_WIZARD_STEPS = "customize-ide-wizard";
  private static final String STEPS_PROVIDER = "provider";
  private static final String ELEMENT_EVALUATION = "evaluation";
  private static final String ATTRIBUTE_EVAL_LICENSE_URL = "license-url";
  private static final String ELEMENT_LICENSING = "licensing";
  private static final String ATTRIBUTE_KEY_CONVERSION_URL = "key-conversion-url";
  private static final String ESSENTIAL_PLUGIN = "essential-plugin";

  private static final String ELEMENT_SUBSCRIPTIONS = "subscriptions";
  @SuppressWarnings("SpellCheckingInspection") private static final String ATTRIBUTE_SUBSCRIPTIONS_FORM_ID = "formid";
  private static final String ATTRIBUTE_SUBSCRIPTIONS_NEWS_KEY = "news-key";
  private static final String ATTRIBUTE_SUBSCRIPTIONS_NEWS_VALUE = "news-value";
  private static final String ATTRIBUTE_SUBSCRIPTIONS_TIPS_KEY = "tips-key";
  private static final String ATTRIBUTE_SUBSCRIPTIONS_TIPS_AVAILABLE = "tips-available";
  private static final String ATTRIBUTE_SUBSCRIPTIONS_ADDITIONAL_FORM_DATA = "additional-form-data";
  private static final String PROGRESS_SLIDE = "progressSlide";
  private static final String PROGRESS_PERCENT = "progressPercent";

  static final String DEFAULT_PLUGINS_HOST = "https://plugins.jetbrains.com";
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
    Element versionElement = getChild(element, ELEMENT_VERSION);
    if (versionElement != null) {
      myMajorVersion = versionElement.getAttributeValue(ATTRIBUTE_MAJOR);
      myMinorVersion = versionElement.getAttributeValue(ATTRIBUTE_MINOR);
      myMicroVersion = versionElement.getAttributeValue(ATTRIBUTE_MICRO);
      myPatchVersion = versionElement.getAttributeValue(ATTRIBUTE_PATCH);
      myFullVersionFormat = versionElement.getAttributeValue(ATTRIBUTE_FULL);
      myCodeName = versionElement.getAttributeValue(ATTRIBUTE_CODENAME);
      myEAP = Boolean.parseBoolean(versionElement.getAttributeValue(ATTRIBUTE_EAP));
      myVersionSuffix = versionElement.getAttributeValue("suffix");
      if (myVersionSuffix == null && myEAP) {
        myVersionSuffix = "EAP";
      }
    }

    Element companyElement = getChild(element, ELEMENT_COMPANY);
    if (companyElement != null) {
      myCompanyName = companyElement.getAttributeValue(ATTRIBUTE_NAME, myCompanyName);
      //noinspection TestOnlyProblems
      myShortCompanyName = companyElement.getAttributeValue("shortName", shortenCompanyName(myCompanyName));
      myCompanyUrl = companyElement.getAttributeValue(ATTRIBUTE_URL, myCompanyUrl);
      myCopyrightStart = companyElement.getAttributeValue(COPYRIGHT_START, myCopyrightStart);
    }

    Element buildElement = getChild(element, ELEMENT_BUILD);
    if (buildElement != null) {
      readBuildInfo(buildElement);
    }

    Element logoElement = getChild(element, ELEMENT_LOGO);
    if (logoElement != null) {
      readLogoInfo(logoElement);
    }

    Element aboutLogoElement = getChild(element, ELEMENT_ABOUT);
    if (aboutLogoElement != null) {
      myAboutImageUrl = aboutLogoElement.getAttributeValue(ATTRIBUTE_URL);

      String v = aboutLogoElement.getAttributeValue(ATTRIBUTE_ABOUT_FOREGROUND_COLOR);
      if (v != null) {
        myAboutForeground = parseColor(v);
      }
      v = aboutLogoElement.getAttributeValue(ATTRIBUTE_ABOUT_COPYRIGHT_FOREGROUND_COLOR);
      if (v != null) {
        myCopyrightForeground = parseColor(v);
      }

      String c = aboutLogoElement.getAttributeValue(ATTRIBUTE_ABOUT_LINK_COLOR);
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

    Element iconElement = getChild(element, ELEMENT_ICON);
    if (iconElement != null) {
      myIconUrl = iconElement.getAttributeValue("size32");
      mySmallIconUrl = iconElement.getAttributeValue(ATTRIBUTE_SIZE16, mySmallIconUrl);
      myBigIconUrl = getAttributeValue(iconElement, "size128");
      String toolWindowIcon = getAttributeValue(iconElement, ATTRIBUTE_SIZE12);
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

    Element packageElement = getChild(element, ELEMENT_PACKAGE);
    if (packageElement != null) {
      myPackageCode = packageElement.getAttributeValue(ATTRIBUTE_CODE);
    }

    Element showLicensee = getChild(element, ELEMENT_LICENSEE);
    if (showLicensee != null) {
      myShowLicensee = Boolean.parseBoolean(showLicensee.getAttributeValue(ATTRIBUTE_SHOW));
    }

    Element welcomeScreen = getChild(element, WELCOME_SCREEN_ELEMENT_NAME);
    if (welcomeScreen != null) {
      myWelcomeScreenLogoUrl = welcomeScreen.getAttributeValue(LOGO_URL_ATTR);
    }

    Element wizardSteps = getChild(element, CUSTOMIZE_IDE_WIZARD_STEPS);
    if (wizardSteps != null) {
      myCustomizeIDEWizardStepsProvider = wizardSteps.getAttributeValue(STEPS_PROVIDER);
    }

    Element helpElement = getChild(element, HELP_ELEMENT_NAME);
    if (helpElement != null) {
      String webHelpUrl = getAttributeValue(helpElement, ATTRIBUTE_WEBHELP_URL);
      if (webHelpUrl != null) {
        myWebHelpUrl = webHelpUrl;
      }

      String attValue = helpElement.getAttributeValue(ATTRIBUTE_HAS_HELP);
      myHasHelp = attValue == null || Boolean.parseBoolean(attValue); // Default is true

      attValue = helpElement.getAttributeValue(ATTRIBUTE_HAS_CONTEXT_HELP);
      myHasContextHelp = attValue == null || Boolean.parseBoolean(attValue); // Default is true
    }

    Element updateUrls = getChild(element, UPDATE_URLS_ELEMENT_NAME);
    myUpdateUrls = updateUrls == null ? null : new UpdateUrlsImpl(updateUrls);

    @SuppressWarnings("DuplicatedCode")
    Element documentationElement = getChild(element, ELEMENT_DOCUMENTATION);
    if (documentationElement != null) {
      myDocumentationUrl = documentationElement.getAttributeValue(ATTRIBUTE_URL);
    }

    Element supportElement = getChild(element, ELEMENT_SUPPORT);
    if (supportElement != null) {
      mySupportUrl = supportElement.getAttributeValue(ATTRIBUTE_URL);
    }

    Element youtrackElement = getChild(element, ELEMENT_YOUTRACK);
    if (youtrackElement != null) {
      myYoutrackUrl = youtrackElement.getAttributeValue(ATTRIBUTE_URL);
    }

    Element feedbackElement = getChild(element, ELEMENT_FEEDBACK);
    if (feedbackElement != null) {
      myFeedbackUrl = feedbackElement.getAttributeValue(ATTRIBUTE_URL);
    }

    Element whatsNewElement = getChild(element, ELEMENT_WHATS_NEW);
    if (whatsNewElement != null) {
      myWhatsNewUrl = whatsNewElement.getAttributeValue(ATTRIBUTE_URL);
    }

    readPluginInfo(getChild(element, ELEMENT_PLUGINS));

    Element keymapElement = getChild(element, ELEMENT_KEYMAP);
    if (keymapElement != null) {
      myWinKeymapUrl = keymapElement.getAttributeValue(ATTRIBUTE_WINDOWS_URL);
      myMacKeymapUrl = keymapElement.getAttributeValue(ATTRIBUTE_MAC_URL);
    }

    List<Element> essentialPluginsElements = getChildren(element, ESSENTIAL_PLUGIN);
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

    Element statisticsElement = getChild(element, ELEMENT_STATISTICS);
    if (statisticsElement != null) {
      myEventLogSettingsUrl = statisticsElement.getAttributeValue(ATTRIBUTE_EVENT_LOG_STATISTICS_SETTINGS);
    }
    else {
      myEventLogSettingsUrl = "https://resources.jetbrains.com/storage/fus/config/v2/%s/%s.json";
    }

    Element tvElement = getChild(element, ELEMENT_JB_TV);
    if (tvElement != null) {
      myJetBrainsTvUrl = tvElement.getAttributeValue(ATTRIBUTE_URL);
    }

    Element evaluationElement = getChild(element, ELEMENT_EVALUATION);
    if (evaluationElement != null) {
      String url = getAttributeValue(evaluationElement, ATTRIBUTE_EVAL_LICENSE_URL);
      if (url != null) {
        myEvalLicenseUrl = url.trim();
      }
    }

    Element licensingElement = getChild(element, ELEMENT_LICENSING);
    if (licensingElement != null) {
      String url = getAttributeValue(licensingElement, ATTRIBUTE_KEY_CONVERSION_URL);
      if (url != null) {
        myKeyConversionUrl = url.trim();
      }
    }

    Element subscriptionsElement = getChild(element, ELEMENT_SUBSCRIPTIONS);
    if (subscriptionsElement != null) {
      mySubscriptionFormId = subscriptionsElement.getAttributeValue(ATTRIBUTE_SUBSCRIPTIONS_FORM_ID);
      mySubscriptionNewsKey = subscriptionsElement.getAttributeValue(ATTRIBUTE_SUBSCRIPTIONS_NEWS_KEY);
      mySubscriptionNewsValue = subscriptionsElement.getAttributeValue(ATTRIBUTE_SUBSCRIPTIONS_NEWS_VALUE, "yes");
      mySubscriptionTipsKey = subscriptionsElement.getAttributeValue(ATTRIBUTE_SUBSCRIPTIONS_TIPS_KEY);
      mySubscriptionTipsAvailable = Boolean.parseBoolean(subscriptionsElement.getAttributeValue(ATTRIBUTE_SUBSCRIPTIONS_TIPS_AVAILABLE));
      mySubscriptionAdditionalFormData = subscriptionsElement.getAttributeValue(ATTRIBUTE_SUBSCRIPTIONS_ADDITIONAL_FORM_DATA);
    }
  }

  private void readLogoInfo(@NotNull Element element) {
    mySplashImageUrl = getAttributeValue(element, ATTRIBUTE_URL);
    String v = getAttributeValue(element, ATTRIBUTE_PROGRESS_COLOR);
    if (v != null) {
      myProgressColor = parseColor(v);
    }

    v = getAttributeValue(element, ATTRIBUTE_PROGRESS_TAIL_ICON);
    if (v != null) {
      myProgressTailIconName = v;
    }

    v = getAttributeValue(element, ATTRIBUTE_PROGRESS_HEIGHT);
    if (v != null) {
      myProgressHeight = Integer.parseInt(v);
    }

    v = getAttributeValue(element, ATTRIBUTE_PROGRESS_Y);
    if (v != null) {
      myProgressY = Integer.parseInt(v);
    }

    for (Element child : getChildren(element, PROGRESS_SLIDE)) {
      String slideUrl = child.getAttributeValue(ATTRIBUTE_URL);
      assert slideUrl != null;
      String progressPercentString = child.getAttributeValue(PROGRESS_PERCENT);
      assert progressPercentString != null;

      int progressPercentInt = Integer.parseInt(progressPercentString);
      assert (progressPercentInt <= 100 && progressPercentInt >= 0);

      float progressPercentFloat = (float) progressPercentInt / 100;
      myProgressSlides.add(new ProgressSlide(slideUrl, progressPercentFloat));
    }
  }

  public static @NotNull ApplicationInfoEx getShadowInstance() {
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

  @Override
  public Calendar getBuildDate() {
    return myBuildDate;
  }

  @Override
  public Calendar getMajorReleaseBuildDate() {
    return myMajorReleaseBuildDate != null ? myMajorReleaseBuildDate : myBuildDate;
  }

  @Override
  public @NotNull BuildNumber getBuild() {
    return Objects.requireNonNull(BuildNumber.fromString(myBuildNumber));
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
      result = StringUtilRt.notNullize(myMajorVersion, "0") + '.' + StringUtilRt.notNullize(myMinorVersion, "0");
    }
    if (!StringUtilRt.isEmpty(myVersionSuffix)) {
      result += " " + myVersionSuffix;
    }
    return result;
  }

  @Override
  public @NotNull String getStrictVersion() {
    return myMajorVersion + "." + myMinorVersion + "." + StringUtilRt.notNullize(myMicroVersion, "0") + "." + StringUtilRt.notNullize(myPatchVersion, "0");
  }

  @Override
  public String getVersionName() {
    String fullName = ApplicationNamesInfo.getInstance().getFullProductName();
    if (myEAP && !StringUtilRt.isEmptyOrSpaces(myCodeName)) {
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
    return isEAP() && mySmallSvgEapIconUrl != null ? mySmallSvgEapIconUrl : mySmallSvgIconUrl;
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
  @SuppressWarnings("SSBasedInspection")
  public @Nullable int[] getAboutLogoRect() {
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
  public List<ProgressSlide> getProgressSlides() {
    return myProgressSlides;
  }

  private static @Nullable String getAttributeValue(@NotNull Element element, @NotNull String name) {
    String value = element.getAttributeValue(name);
    return (value == null || value.isEmpty()) ? null : value;
  }

  private void readBuildInfo(@NotNull Element element) {
    myBuildNumber = getAttributeValue(element, ATTRIBUTE_NUMBER);
    myApiVersion = getAttributeValue(element, ATTRIBUTE_API_VERSION);

    String dateString = element.getAttributeValue(ATTRIBUTE_DATE);
    if ("__BUILD_DATE__".equals(dateString)) {
      myBuildDate = new GregorianCalendar();
      try (JarFile bootstrapJar = new JarFile(PathManager.getHomePath() + "/lib/bootstrap.jar")) {
        // META-INF is always updated on build
        JarEntry jarEntry = bootstrapJar.entries().nextElement();
        myBuildDate.setTime(new Date(jarEntry.getTime()));
      }
      catch (Exception ignore) { }
    }
    else {
      myBuildDate = dateString == null ? Calendar.getInstance() : parseDate(dateString);
    }

    String majorReleaseDateString = element.getAttributeValue(ATTRIBUTE_MAJOR_RELEASE_DATE);
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
      String url = element.getAttributeValue(ATTRIBUTE_URL);
      if (url != null) {
        pluginManagerUrl = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
      }

      String listUrl = element.getAttributeValue(ATTRIBUTE_LIST_URL);
      if (listUrl != null) {
        pluginsListUrl = listUrl;
      }

      String channelListUrl = element.getAttributeValue(ATTRIBUTE_CHANNEL_LIST_URL);
      if (channelListUrl != null) {
        myChannelsListUrl = channelListUrl;
      }

      String downloadUrl = element.getAttributeValue(ATTRIBUTE_DOWNLOAD_URL);
      if (downloadUrl != null) {
        myPluginsDownloadUrl = downloadUrl;
      }

      String builtinPluginsUrl = element.getAttributeValue(ATTRIBUTE_BUILTIN_URL);
      if (StringUtil.isNotEmpty(builtinPluginsUrl)) {
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
  @SuppressWarnings("SSBasedInspection")
  @TestOnly
  static String shortenCompanyName(@NotNull String name) {
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

  public static long parseColor(@NotNull String colorString) {
    return Long.parseLong(colorString, 16);
  }

  @Override
  public boolean isEssentialPlugin(@NotNull String pluginId) {
    return PluginManagerCore.CORE_PLUGIN_ID.equals(pluginId) || isEssentialPlugin(PluginId.getId(pluginId));
  }

  @Override
  public boolean isEssentialPlugin(@NotNull PluginId pluginId) {
    return PluginManagerCore.CORE_ID == pluginId || Collections.binarySearch(myEssentialPluginsIds, pluginId) >= 0;
  }

  public @NotNull List<PluginId> getEssentialPluginsIds() {
    return myEssentialPluginsIds;
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

  private static volatile boolean myInStressTest;
  public static boolean isInStressTest() {
    return myInStressTest;
  }
  @TestOnly
  public static void setInStressTest(boolean inStressTest) {
    myInStressTest = inStressTest;
  }
}