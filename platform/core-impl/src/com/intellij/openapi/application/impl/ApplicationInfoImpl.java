/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.application.impl;

import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.components.NamedComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.JBColor;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.PlatformUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Document;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.text.MessageFormat;
import java.util.*;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class ApplicationInfoImpl extends ApplicationInfoEx implements JDOMExternalizable, NamedComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.application.impl.ApplicationInfoImpl");

  private String myCodeName = null;
  private String myMajorVersion = null;
  private String myMinorVersion = null;
  private String myMicroVersion = null;
  private String myPatchVersion = null;
  private String myFullVersion = null;
  private String myBuildNumber = null;
  private String myApiVersion = null;
  private String myCompanyName = "JetBrains s.r.o.";
  private String myCompanyUrl = "https://www.jetbrains.com/";
  private Color myProgressColor = null;
  private Color myCopyrightForeground = JBColor.BLACK;
  private Color myAboutForeground = JBColor.BLACK;
  private Color myAboutLinkColor = null;
  private String myProgressTailIconName = null;
  private Icon myProgressTailIcon = null;

  private int myProgressY = 350;
  private String mySplashImageUrl = null;
  private String myAboutImageUrl = null;
  private Color mySplashTextColor = new Color(0, 35, 135);  // idea blue
  private String myIconUrl = "/icon.png";
  private String mySmallIconUrl = "/icon_small.png";
  private String myBigIconUrl = null;
  private String myToolWindowIconUrl = "/toolwindows/toolWindowProject.png";
  private String myWelcomeScreenLogoUrl = null;
  private String myEditorBackgroundImageUrl = null;

  private Calendar myBuildDate = null;
  private Calendar myMajorReleaseBuildDate = null;
  private String myPackageCode = null;
  private boolean myShowLicensee = true;
  private String myCustomizeIDEWizardStepsProvider;
  private UpdateUrls myUpdateUrls;
  private String myDocumentationUrl;
  private String mySupportUrl;
  private String myEAPFeedbackUrl;
  private String myReleaseFeedbackUrl;
  private String myPluginManagerUrl;
  private String myPluginsListUrl;
  private String myPluginsDownloadUrl;
  private String myBuiltinPluginsUrl;
  private String myWhatsNewUrl;
  private String myWinKeymapUrl;
  private String myMacKeymapUrl;
  private boolean myEAP;
  private boolean myHasHelp = true;
  private boolean myHasContextHelp = true;
  private String myHelpFileName = "ideahelp.jar";
  private String myHelpRootName = "idea";
  private String myWebHelpUrl = "https://www.jetbrains.com/idea/webhelp/";
  private List<PluginChooserPage> myPluginChooserPages = new ArrayList<PluginChooserPage>();
  private String[] myEssentialPluginsIds;
  private String myStatisticsSettingsUrl;
  private String myStatisticsServiceUrl;
  private String myStatisticsServiceKey;
  private String myThirdPartySoftwareUrl;
  private String myJetbrainsTvUrl;
  private String myEvalLicenseUrl = "https://www.jetbrains.com/company/useterms.html";

  private Rectangle myAboutLogoRect;

  private static final String IDEA_PATH = "/idea/";
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
  private static final String ATTRIBUTE_TEXT_COLOR = "textcolor";
  private static final String ATTRIBUTE_PROGRESS_COLOR = "progressColor";
  private static final String ATTRIBUTE_ABOUT_FOREGROUND_COLOR = "foreground";
  private static final String ATTRIBUTE_ABOUT_COPYRIGHT_FOREGROUND_COLOR = "copyrightForeground";
  private static final String ATTRIBUTE_ABOUT_LINK_COLOR = "linkColor";
  private static final String ATTRIBUTE_PROGRESS_Y = "progressY";
  private static final String ATTRIBUTE_PROGRESS_TAIL_ICON = "progressTailIcon";
  private static final String ELEMENT_ABOUT = "about";
  private static final String ELEMENT_ICON = "icon";
  private static final String ATTRIBUTE_SIZE32 = "size32";
  private static final String ATTRIBUTE_SIZE128 = "size128";
  private static final String ATTRIBUTE_SIZE16 = "size16";
  private static final String ATTRIBUTE_SIZE12 = "size12";
  private static final String ELEMENT_PACKAGE = "package";
  private static final String ATTRIBUTE_CODE = "code";
  private static final String ELEMENT_LICENSEE = "licensee";
  private static final String ATTRIBUTE_SHOW = "show";
  private static final String WELCOME_SCREEN_ELEMENT_NAME = "welcome-screen";
  private static final String LOGO_URL_ATTR = "logo-url";
  private static final String ELEMENT_EDITOR = "editor";
  private static final String BACKGROUND_URL_ATTR = "background-url";
  private static final String UPDATE_URLS_ELEMENT_NAME = "update-urls";
  private static final String XML_EXTENSION = ".xml";
  private static final String ATTRIBUTE_EAP = "eap";
  private static final String HELP_ELEMENT_NAME = "help";
  private static final String ATTRIBUTE_HELP_FILE = "file";
  private static final String ATTRIBUTE_HELP_ROOT = "root";
  private static final String PLUGINS_PAGE_ELEMENT_NAME = "plugins-page";
  private static final String ELEMENT_DOCUMENTATION = "documentation";
  private static final String ELEMENT_SUPPORT = "support";
  private static final String ELEMENT_FEEDBACK = "feedback";
  private static final String ATTRIBUTE_RELEASE_URL = "release-url";
  private static final String ATTRIBUTE_EAP_URL = "eap-url";
  private static final String ELEMENT_PLUGINS = "plugins";
  private static final String ATTRIBUTE_LIST_URL = "list-url";
  private static final String ATTRIBUTE_DOWNLOAD_URL = "download-url";
  private static final String ATTRIBUTE_BUILTIN_URL = "builtin-url";
  private static final String ATTRIBUTE_WEBHELP_URL = "webhelp-url";
  private static final String ATTRIBUTE_HAS_HELP = "has-help";
  private static final String ATTRIBUTE_HAS_CONTEXT_HELP = "has-context-help";
  private static final String ELEMENT_WHATSNEW = "whatsnew";
  private static final String ELEMENT_KEYMAP = "keymap";
  private static final String ATTRIBUTE_WINDOWS_URL = "win";
  private static final String ATTRIBUTE_MAC_URL = "mac";
  private static final String ELEMENT_STATISTICS = "statistics";
  private static final String ATTRIBUTE_STATISTICS_SETTINGS = "settings";
  private static final String ATTRIBUTE_STATISTICS_SERVICE = "service";
  private static final String ATTRIBUTE_STATISTICS_SERVICE_KEY = "service-key";
  private static final String ELEMENT_THIRD_PARTY = "third-party";
  private static final String ELEMENT_JB_TV = "jetbrains-tv";
  private static final String CUSTOMIZE_IDE_WIZARD_STEPS = "customize-ide-wizard";
  private static final String STEPS_PROVIDER = "provider";
  private static final String ELEMENT_EVALUATION = "evaluation";
  private static final String ATTRIBUTE_EVAL_LICENSE_URL = "license-url";
  private static final String ESSENTIAL_PLUGIN = "essential-plugin";

  private static final String DEFAULT_PLUGINS_HOST = "http://plugins.jetbrains.com";

  @Override
  public Calendar getBuildDate() {
    return myBuildDate;
  }

  @Override
  public Calendar getMajorReleaseBuildDate() {
    return myMajorReleaseBuildDate != null ? myMajorReleaseBuildDate : myBuildDate;
  }

  @Override
  public BuildNumber getBuild() {
    return BuildNumber.fromString(myBuildNumber, getProductPrefix());
  }

  private static String getProductPrefix() {
    String prefix = null;
    if (PlatformUtils.isIdeaCommunity()) {
      prefix = "IC";
    }
    else if (PlatformUtils.isIdeaUltimate()) {
      prefix = "IU";
    }
    return prefix;
  }

  @Override
  public String getApiVersion() {
    if (myApiVersion != null) {
      return BuildNumber.fromString(myApiVersion, getBuild().getProductCode()).asString();
    }
    return getBuild().asString();
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
  public String getFullVersion() {
    if (myFullVersion == null) {
      if (!StringUtil.isEmptyOrSpaces(myMajorVersion)) {
        if (!StringUtil.isEmptyOrSpaces(myMinorVersion)) {
          return myMajorVersion + "." + myMinorVersion;
        }
        else {
          return myMajorVersion + ".0";
        }
      }
      else {
        return getVersionName();
      }
    } else {
      return MessageFormat.format(myFullVersion, myMajorVersion, myMinorVersion, myMicroVersion, myPatchVersion);
    }
  }

  @Override
  public String getStrictVersion() {
    return myMajorVersion + "." + myMinorVersion + "." + StringUtil.notNullize(myMicroVersion, "0") + "." + StringUtil.notNullize(myPatchVersion, "0");
  }

  @Override
  public String getVersionName() {
    final String fullName = ApplicationNamesInfo.getInstance().getFullProductName();
    if (myEAP && !StringUtil.isEmptyOrSpaces(myCodeName)) {
      return fullName + " (" + myCodeName + ")";
    }
    return fullName;
  }

  @Override
  public String getHelpURL() {
    return "jar:file:///" + getHelpJarPath() + "!/" + myHelpRootName;
  }

  @Override
  public String getCompanyName() {
    return myCompanyName;
  }

  @Override
  public String getCompanyURL() {
    return myCompanyUrl;
  }

  private String getHelpJarPath() {
    return PathManager.getHomePath() + File.separator + "help" + File.separator + myHelpFileName;
  }

  @Override
  public String getSplashImageUrl() {
    return mySplashImageUrl;
  }

  @Override
  public Color getSplashTextColor() {
    return mySplashTextColor;
  }

  @Override
  public String getAboutImageUrl() {
    return myAboutImageUrl;
  }

  public Color getProgressColor() {
    return myProgressColor;
  }

  public Color getCopyrightForeground() {
    return myCopyrightForeground;
  }

  public int getProgressY() {
    return myProgressY;
  }

  @Nullable
  public Icon getProgressTailIcon() {
    if (myProgressTailIcon == null && myProgressTailIconName != null) {
      myProgressTailIcon = IconLoader.getIcon(myProgressTailIconName);
    }
    return myProgressTailIcon;
  }

  @Override
  public String getIconUrl() {
    return myIconUrl;
  }

  @Override
  public String getSmallIconUrl() {
    return mySmallIconUrl;
  }

  @Override
  @Nullable
  public String getBigIconUrl() {
    return myBigIconUrl;
  }

  @Override
  public String getToolWindowIconUrl() {
    return myToolWindowIconUrl;
  }

  @Override
  public String getWelcomeScreenLogoUrl() {
    return myWelcomeScreenLogoUrl;
  }

  @Nullable
  @Override
  public String getCustomizeIDEWizardStepsProvider() {
    return myCustomizeIDEWizardStepsProvider;
  }

  @Override
  public String getEditorBackgroundImageUrl() {
    return myEditorBackgroundImageUrl;
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
  public UpdateUrls getUpdateUrls() {
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
  public String getEAPFeedbackUrl() {
    return myEAPFeedbackUrl;
  }

  @Override
  public String getReleaseFeedbackUrl() {
    return myReleaseFeedbackUrl;
  }

  @Override
  public String getPluginManagerUrl() {
    return myPluginManagerUrl;
  }

  @Override
  public String getPluginsListUrl() {
    return myPluginsListUrl;
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
  public Color getAboutForeground() {
    return myAboutForeground;
  }

  public Color getAboutLinkColor() {
    return myAboutLinkColor;
  }

  @Override
  public String getFullApplicationName() {
    StringBuilder buffer = new StringBuilder();
    buffer.append(getVersionName());
    buffer.append(" ");
    if (getMajorVersion() != null && !isEAP() && !isBetaOrRC()) {
      buffer.append(getFullVersion());
    }
    else {
      buffer.append(getBuild().asStringWithAllDetails());
    }
    return buffer.toString();
  }

  @Override
  public boolean showLicenseeInfo() {
    return myShowLicensee;
  }

  public String getStatisticsSettingsUrl() {
    return myStatisticsSettingsUrl;
  }

  public String getStatisticsServiceUrl() {
    return myStatisticsServiceUrl;
  }

  public String getStatisticsServiceKey() {
    return myStatisticsServiceKey;
  }

  @Override
  public String getThirdPartySoftwareURL() {
    return myThirdPartySoftwareUrl;
  }

  @Override
  public String getJetbrainsTvUrl() {
    return myJetbrainsTvUrl;
  }

  @Override
  public String getEvalLicenseUrl() {
    return myEvalLicenseUrl;
  }

  @Override
  public Rectangle getAboutLogoRect() {
    return myAboutLogoRect;
  }

  private static ApplicationInfoImpl ourShadowInstance;

  public boolean isBetaOrRC() {
    String minor = getMinorVersion();
    if (minor != null) {
      if (minor.contains("RC") || minor.contains("Beta") || minor.contains("beta")) {
        return true;
      }
    }
    return false;
  }

  public static ApplicationInfoEx getShadowInstance() {
    if (ourShadowInstance == null) {
      ourShadowInstance = new ApplicationInfoImpl();
      try {
        Document doc = JDOMUtil.loadDocument(ApplicationInfoImpl.class, IDEA_PATH + ApplicationNamesInfo.getComponentName() + XML_EXTENSION);
        ourShadowInstance.readExternal(doc.getRootElement());
      }
      catch (FileNotFoundException e) {
        LOG.error("Resource is not in classpath or wrong platform prefix: " + System.getProperty(PlatformUtils.PLATFORM_PREFIX_KEY), e);
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }
    return ourShadowInstance;
  }

  @Override
  public void readExternal(Element parentNode) throws InvalidDataException {
    Element versionElement = parentNode.getChild(ELEMENT_VERSION);
    if (versionElement != null) {
      myMajorVersion = versionElement.getAttributeValue(ATTRIBUTE_MAJOR);
      myMinorVersion = versionElement.getAttributeValue(ATTRIBUTE_MINOR);
      myMicroVersion = versionElement.getAttributeValue(ATTRIBUTE_MICRO);
      myPatchVersion = versionElement.getAttributeValue(ATTRIBUTE_PATCH);
      myFullVersion = versionElement.getAttributeValue(ATTRIBUTE_FULL);
      myCodeName = versionElement.getAttributeValue(ATTRIBUTE_CODENAME);
      myEAP = Boolean.parseBoolean(versionElement.getAttributeValue(ATTRIBUTE_EAP));
    }

    Element companyElement = parentNode.getChild(ELEMENT_COMPANY);
    if (companyElement != null) {
      myCompanyName = companyElement.getAttributeValue(ATTRIBUTE_NAME, myCompanyName);
      myCompanyUrl = companyElement.getAttributeValue(ATTRIBUTE_URL, myCompanyUrl);
    }

    Element buildElement = parentNode.getChild(ELEMENT_BUILD);
    if (buildElement != null) {
      myBuildNumber = buildElement.getAttributeValue(ATTRIBUTE_NUMBER);
      myApiVersion = buildElement.getAttributeValue(ATTRIBUTE_API_VERSION);
      setBuildNumber(myApiVersion, myBuildNumber);

      String dateString = buildElement.getAttributeValue(ATTRIBUTE_DATE);
      if (dateString.equals("__BUILD_DATE__")) {
        myBuildDate = new GregorianCalendar();
        try {
          final JarFile bootJar = new JarFile(PathManager.getHomePath() + File.separator + "lib" + File.separator + "boot.jar");
          try {
            final JarEntry jarEntry = bootJar.entries().nextElement(); // /META-INF is always updated on build
            myBuildDate.setTime(new Date(jarEntry.getTime()));
          }
          finally {
            bootJar.close();
          }
        }
        catch (Exception ignore) { }
      }
      else {
        myBuildDate = parseDate(dateString);
      }
      String majorReleaseDateString = buildElement.getAttributeValue(ATTRIBUTE_MAJOR_RELEASE_DATE);
      if (majorReleaseDateString != null) {
        myMajorReleaseBuildDate = parseDate(majorReleaseDateString);
      }
    }

    Thread currentThread = Thread.currentThread();
    currentThread.setName(currentThread.getName() + " " + myMajorVersion + "." + myMinorVersion + "#" + myBuildNumber + ", eap:" + myEAP);

    Element logoElement = parentNode.getChild(ELEMENT_LOGO);
    if (logoElement != null) {
      mySplashImageUrl = logoElement.getAttributeValue(ATTRIBUTE_URL);
      mySplashTextColor = parseColor(logoElement.getAttributeValue(ATTRIBUTE_TEXT_COLOR));
      String v = logoElement.getAttributeValue(ATTRIBUTE_PROGRESS_COLOR);
      if (v != null) {
        myProgressColor = parseColor(v);
      }

      v = logoElement.getAttributeValue(ATTRIBUTE_PROGRESS_TAIL_ICON);
      if (v != null) {
        myProgressTailIconName = v;
      }

      v = logoElement.getAttributeValue(ATTRIBUTE_PROGRESS_Y);
      if (v != null) {
        myProgressY = Integer.parseInt(v);
      }
    }

    Element aboutLogoElement = parentNode.getChild(ELEMENT_ABOUT);
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
          myAboutLogoRect =
            new Rectangle(Integer.parseInt(logoX), Integer.parseInt(logoY), Integer.parseInt(logoW), Integer.parseInt(logoH));
        }
        catch (NumberFormatException nfe) {
          // ignore
        }
      }
    }

    Element iconElement = parentNode.getChild(ELEMENT_ICON);
    if (iconElement != null) {
      myIconUrl = iconElement.getAttributeValue(ATTRIBUTE_SIZE32);
      mySmallIconUrl = iconElement.getAttributeValue(ATTRIBUTE_SIZE16);
      myBigIconUrl = iconElement.getAttributeValue(ATTRIBUTE_SIZE128, (String)null);
      final String toolWindowIcon = iconElement.getAttributeValue(ATTRIBUTE_SIZE12);
      if (toolWindowIcon != null) {
        myToolWindowIconUrl = toolWindowIcon;
      }
    }

    Element packageElement = parentNode.getChild(ELEMENT_PACKAGE);
    if (packageElement != null) {
      myPackageCode = packageElement.getAttributeValue(ATTRIBUTE_CODE);
    }

    Element showLicensee = parentNode.getChild(ELEMENT_LICENSEE);
    if (showLicensee != null) {
      myShowLicensee = Boolean.valueOf(showLicensee.getAttributeValue(ATTRIBUTE_SHOW)).booleanValue();
    }

    Element welcomeScreen = parentNode.getChild(WELCOME_SCREEN_ELEMENT_NAME);
    if (welcomeScreen != null) {
      myWelcomeScreenLogoUrl = welcomeScreen.getAttributeValue(LOGO_URL_ATTR);
    }

    Element wizardSteps = parentNode.getChild(CUSTOMIZE_IDE_WIZARD_STEPS);
    if (wizardSteps != null) {
      myCustomizeIDEWizardStepsProvider = wizardSteps.getAttributeValue(STEPS_PROVIDER);
    }

    Element editor = parentNode.getChild(ELEMENT_EDITOR);
    if (editor != null) {
      myEditorBackgroundImageUrl = editor.getAttributeValue(BACKGROUND_URL_ATTR);
    }

    Element helpElement = parentNode.getChild(HELP_ELEMENT_NAME);
    if (helpElement != null) {
      myHelpFileName = helpElement.getAttributeValue(ATTRIBUTE_HELP_FILE);
      myHelpRootName = helpElement.getAttributeValue(ATTRIBUTE_HELP_ROOT);
      final String webHelpUrl = helpElement.getAttributeValue(ATTRIBUTE_WEBHELP_URL);
      if (webHelpUrl != null) {
        myWebHelpUrl = webHelpUrl;
      }

      String attValue = helpElement.getAttributeValue(ATTRIBUTE_HAS_HELP);
      myHasHelp = attValue == null || Boolean.parseBoolean(attValue); // Default is true

      attValue = helpElement.getAttributeValue(ATTRIBUTE_HAS_CONTEXT_HELP);
      myHasContextHelp = attValue == null || Boolean.parseBoolean(attValue); // Default is true
    }

    Element updateUrls = parentNode.getChild(UPDATE_URLS_ELEMENT_NAME);
    myUpdateUrls = new UpdateUrlsImpl(updateUrls);

    Element documentationElement = parentNode.getChild(ELEMENT_DOCUMENTATION);
    if (documentationElement != null) {
      myDocumentationUrl = documentationElement.getAttributeValue(ATTRIBUTE_URL);
    }

    Element supportElement = parentNode.getChild(ELEMENT_SUPPORT);
    if (supportElement != null) {
      mySupportUrl = supportElement.getAttributeValue(ATTRIBUTE_URL);
    }

    Element feedbackElement = parentNode.getChild(ELEMENT_FEEDBACK);
    if (feedbackElement != null) {
      myEAPFeedbackUrl = feedbackElement.getAttributeValue(ATTRIBUTE_EAP_URL);
      myReleaseFeedbackUrl = feedbackElement.getAttributeValue(ATTRIBUTE_RELEASE_URL);
    }

    Element whatsnewElement = parentNode.getChild(ELEMENT_WHATSNEW);
    if (whatsnewElement != null) {
      myWhatsNewUrl = whatsnewElement.getAttributeValue(ATTRIBUTE_URL);
    }

    Element pluginsElement = parentNode.getChild(ELEMENT_PLUGINS);
    if (pluginsElement != null) {
      String url = pluginsElement.getAttributeValue(ATTRIBUTE_URL);
      myPluginManagerUrl = url != null ? url : DEFAULT_PLUGINS_HOST;
      boolean closed = StringUtil.endsWith(myPluginManagerUrl, "/");

      String listUrl = pluginsElement.getAttributeValue(ATTRIBUTE_LIST_URL);
      myPluginsListUrl = listUrl != null ? listUrl : myPluginManagerUrl + (closed ? "" : "/") + "plugins/list/";

      String downloadUrl = pluginsElement.getAttributeValue(ATTRIBUTE_DOWNLOAD_URL);
      myPluginsDownloadUrl = downloadUrl != null ? downloadUrl : myPluginManagerUrl + (closed ? "" : "/") + "pluginManager/";

      if (!getBuild().isSnapshot()) {
        myBuiltinPluginsUrl = pluginsElement.getAttributeValue(ATTRIBUTE_BUILTIN_URL);
      }
    }
    else {
      myPluginManagerUrl = DEFAULT_PLUGINS_HOST;
      myPluginsListUrl = DEFAULT_PLUGINS_HOST + "/plugins/list/";
      myPluginsDownloadUrl = DEFAULT_PLUGINS_HOST + "/pluginManager/";
    }

    final String pluginsHost = System.getProperty("idea.plugins.host");
    if (pluginsHost != null) {
      myPluginsListUrl = myPluginsListUrl.replace(DEFAULT_PLUGINS_HOST, pluginsHost);
      myPluginsDownloadUrl = myPluginsDownloadUrl.replace(DEFAULT_PLUGINS_HOST, pluginsHost);
    }

    Element keymapElement = parentNode.getChild(ELEMENT_KEYMAP);
    if (keymapElement != null) {
      myWinKeymapUrl = keymapElement.getAttributeValue(ATTRIBUTE_WINDOWS_URL);
      myMacKeymapUrl = keymapElement.getAttributeValue(ATTRIBUTE_MAC_URL);
    }

    myPluginChooserPages = new ArrayList<PluginChooserPage>();
    final List children = parentNode.getChildren(PLUGINS_PAGE_ELEMENT_NAME);
    for(Object child: children) {
      myPluginChooserPages.add(new PluginChooserPageImpl((Element) child));
    }

    List<Element> essentialPluginsElements = JDOMUtil.getChildren(parentNode, ESSENTIAL_PLUGIN);
    Collection<String> essentialPluginsIds = ContainerUtil.mapNotNull(essentialPluginsElements, new Function<Element, String>() {
      @Override
      public String fun(Element element) {
        String id = element.getTextTrim();
        return StringUtil.isNotEmpty(id) ? id : null;
      }
    });
    myEssentialPluginsIds = ArrayUtil.toStringArray(essentialPluginsIds);

    Element statisticsElement = parentNode.getChild(ELEMENT_STATISTICS);
    if (statisticsElement != null) {
      myStatisticsSettingsUrl = statisticsElement.getAttributeValue(ATTRIBUTE_STATISTICS_SETTINGS);
      myStatisticsServiceUrl  = statisticsElement.getAttributeValue(ATTRIBUTE_STATISTICS_SERVICE);
      myStatisticsServiceKey  = statisticsElement.getAttributeValue(ATTRIBUTE_STATISTICS_SERVICE_KEY);
    }
    else {
      myStatisticsSettingsUrl = "https://www.jetbrains.com/idea/statistics/stat-assistant.xml";
      myStatisticsServiceUrl  = "https://www.jetbrains.com/idea/statistics/index.jsp";
      myStatisticsServiceKey  = null;
    }

    Element thirdPartyElement = parentNode.getChild(ELEMENT_THIRD_PARTY);
    if (thirdPartyElement != null) {
      myThirdPartySoftwareUrl = thirdPartyElement.getAttributeValue(ATTRIBUTE_URL);
    }

    Element tvElement = parentNode.getChild(ELEMENT_JB_TV);
    if (tvElement != null) {
      myJetbrainsTvUrl = tvElement.getAttributeValue(ATTRIBUTE_URL);
    }

    Element evaluationElement = parentNode.getChild(ELEMENT_EVALUATION);
    if (evaluationElement != null) {
      final String url = evaluationElement.getAttributeValue(ATTRIBUTE_EVAL_LICENSE_URL);
      if (url != null && !url.isEmpty()) {
        myEvalLicenseUrl = url;
      }
    }

  }

  private static void setBuildNumber(String apiVersion, String buildNumber) {
    PluginManagerCore.BUILD_NUMBER = apiVersion != null ? apiVersion : buildNumber;
  }

  private static GregorianCalendar parseDate(final String dateString) {
    @SuppressWarnings("MultipleVariablesInDeclaration")
    int year = 0, month = 0, day = 0, hour = 0, minute = 0;
    try {
      year = Integer.parseInt(dateString.substring(0, 4));
      month = Integer.parseInt(dateString.substring(4, 6));
      day = Integer.parseInt(dateString.substring(6, 8));
      if (dateString.length() > 8) {
        hour = Integer.parseInt(dateString.substring(8, 10));
        minute = Integer.parseInt(dateString.substring(10, 12));
      }
    }
    catch (Exception ignore) { }
    if (month > 0) month--;
    return new GregorianCalendar(year, month, day, hour, minute);
  }

  @SuppressWarnings("UseJBColor")
  private static Color parseColor(final String colorString) {
    final long rgb = Long.parseLong(colorString, 16);
    return new Color((int)rgb, rgb > 0xffffff);
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    throw new WriteExternalException();
  }

  @Override
  public List<PluginChooserPage> getPluginChooserPages() {
    return myPluginChooserPages;
  }

  @Override
  public boolean isEssentialPlugin(@NotNull String pluginId) {
    return PluginManagerCore.CORE_PLUGIN_ID.equals(pluginId) || ArrayUtil.contains(pluginId, myEssentialPluginsIds);
  }

  @Override
  @NotNull
  public String getComponentName() {
    return ApplicationNamesInfo.getComponentName();
  }

  private static class UpdateUrlsImpl implements UpdateUrls {
    private String myCheckingUrl;
    private String myPatchesUrl;

    private UpdateUrlsImpl(Element element) {
      if (element != null) {
        myCheckingUrl = element.getAttributeValue("check");
        myPatchesUrl = element.getAttributeValue("patches");
      }
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

  private static class PluginChooserPageImpl implements PluginChooserPage {
    private final String myTitle;
    private final String myCategory;
    private final String myDependentPlugin;

    private PluginChooserPageImpl(Element e) {
      myTitle = e.getAttributeValue("title");
      myCategory = e.getAttributeValue("category");
      myDependentPlugin = e.getAttributeValue("depends");
    }

    @Override
    public String getTitle() {
      return myTitle;
    }

    @Override
    public String getCategory() {
      return myCategory;
    }

    @Override
    public String getDependentPlugin() {
      return myDependentPlugin;
    }
  }

  private static volatile boolean myInPerformanceTest;
  public static boolean isInPerformanceTest() {
    return myInPerformanceTest;
  }
  public static void setInPerformanceTest(boolean inPerformanceTest) {
    myInPerformanceTest = inPerformanceTest;
  }
}
