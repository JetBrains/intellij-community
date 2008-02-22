
package com.intellij.openapi.application.impl;

import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Document;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import java.util.Calendar;
import java.util.GregorianCalendar;

public class ApplicationInfoImpl extends ApplicationInfoEx implements JDOMExternalizable, ApplicationComponent {

  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.application.impl.ApplicationInfoImpl");

  @NonNls private final static String BUILD_STUB = "__BUILD_NUMBER__";
  private String myCodeName = null;
  private String myMajorVersion = null;
  private String myMinorVersion = null;
  private String myBuildNumber = null;
  private String myLogoUrl = null;
  private String myAboutLogoUrl = null;
  private Calendar myBuildDate = null;
  private String myPackageCode = null;
  private boolean myShowLicensee = true;
  private String myWelcomeScreenCaptionUrl;
  private String myWelcomeScreenDeveloperSloganUrl;
  private UpdateUrls myUpdateUrls;
  private UpdateUrls myEapUpdateUrls;
  private boolean myEAP;

  @NonNls private static final String IDEA_PATH = "/idea/";
  @NonNls private static final String ELEMENT_VERSION = "version";
  @NonNls private static final String ATTRIBUTE_MAJOR = "major";
  @NonNls private static final String ATTRIBUTE_MINOR = "minor";
  @NonNls private static final String ATTRIBUTE_CODENAME = "codename";
  @NonNls private static final String ELEMENT_BUILD = "build";
  @NonNls private static final String ATTRIBUTE_NUMBER = "number";
  @NonNls private static final String ATTRIBUTE_DATE = "date";
  @NonNls private static final String ELEMENT_LOGO = "logo";
  @NonNls private static final String ATTRIBUTE_URL = "url";
  @NonNls private static final String ELEMENT_ABOUT = "about";
  @NonNls private static final String ELEMENT_PACKAGE = "package";
  @NonNls private static final String ATTRIBUTE_CODE = "code";
  @NonNls private static final String ELEMENT_LICENSEE = "licensee";
  @NonNls private static final String ATTRIBUTE_SHOW = "show";
  @NonNls private static final String WELCOME_SCREEN_ELEMENT_NAME = "welcome-screen";
  @NonNls private static final String CAPTION_URL_ATTR = "caption-url";
  @NonNls private static final String SLOGAN_URL_ATTR = "slogan-url";
  @NonNls private static final String UPDATE_URLS_ELEMENT_NAME = "update-urls";
  @NonNls private static final String EAP_UPDATE_URLS_ELEMENT_NAME = "eap-update-urls";
  @NonNls private static final String XML_EXTENSION = ".xml";
  @NonNls private static final String ATTRIBUTE_EAP = "eap";

  public void initComponent() { }

  public void disposeComponent() {
  }

  public Calendar getBuildDate() {
    return myBuildDate;
  }

  public String getBuildNumber() {
    return myBuildNumber;
  }

  public String getMajorVersion() {
    return myMajorVersion;
  }

  public String getMinorVersion() {
    return myMinorVersion;
  }

  public String getVersionName() {
    final String fullName = ApplicationNamesInfo.getInstance().getFullProductName();
    if (myEAP) {
      return fullName + " (" + myCodeName + ")";
    }
    return fullName;
  }

  public String getLogoUrl() {
    return myLogoUrl;
  }

  public String getAboutLogoUrl() {
    return myAboutLogoUrl;
  }

  public String getPackageCode() {
    return myPackageCode;
  }

  public String getWelcomeScreenCaptionUrl() {
    return myWelcomeScreenCaptionUrl;
  }

  public String getWelcomeScreenDeveloperSloganUrl() {
    return myWelcomeScreenDeveloperSloganUrl;
  }

  public boolean isEAP() {
    return myEAP;
  }

  public UpdateUrls getUpdateUrls() {
    return isEAP() ? myEapUpdateUrls :  myUpdateUrls;
  }

  public UpdateUrls getEapUpdateUrls() {
    return myEapUpdateUrls;
  }

  public String getFullApplicationName() {
    @NonNls StringBuffer buffer = new StringBuffer();
    buffer.append(getVersionName());
    buffer.append(" ");
    if (getMajorVersion() != null && !isEAP()) {
      buffer.append(getMajorVersion());

      if (getMinorVersion() != null && getMinorVersion().length() > 0){
        buffer.append(".");
        buffer.append(getMinorVersion());
      }
    }
    else {
      String bn = getBuildNumber();
      if (!BUILD_STUB.equals(bn)) {
        buffer.append('#');
        buffer.append(bn);
      }
      else {
        buffer.append("DevVersion");
      }
    }
    return buffer.toString();
  }

  public boolean showLicenseeInfo() {
    return myShowLicensee;
  }

  public static ApplicationInfoEx getShadowInstance() {
    ApplicationInfoImpl instance = new ApplicationInfoImpl();
    try {
      Document doc = JDOMUtil.loadDocument(ApplicationInfoImpl.class.getResourceAsStream(IDEA_PATH +
                                                                                         ApplicationNamesInfo.getComponentName() + XML_EXTENSION));
      instance.readExternal(doc.getRootElement());
    }
    catch (Exception e) {
      LOG.error(e);
    }
    return instance;
  }

  public void readExternal(Element parentNode) throws InvalidDataException {
    Element versionElement = parentNode.getChild(ELEMENT_VERSION);
    if (versionElement != null) {
      myMajorVersion = versionElement.getAttributeValue(ATTRIBUTE_MAJOR);
      myMinorVersion = versionElement.getAttributeValue(ATTRIBUTE_MINOR);
      myCodeName = versionElement.getAttributeValue(ATTRIBUTE_CODENAME);
      myEAP = Boolean.parseBoolean(versionElement.getAttributeValue(ATTRIBUTE_EAP));
    }

    Element buildElement = parentNode.getChild(ELEMENT_BUILD);
    if (buildElement != null) {
      myBuildNumber = buildElement.getAttributeValue(ATTRIBUTE_NUMBER);
      String dateString = buildElement.getAttributeValue(ATTRIBUTE_DATE);
      int year = 0;
      int month = 0;
      int day = 0;
      try {
        year = new Integer(dateString.substring(0, 4)).intValue();
        month = new Integer(dateString.substring(4, 6)).intValue();
        day = new Integer(dateString.substring(6, 8)).intValue();
      }
      catch (Exception ex) {
        //ignore
      }
      if (month > 0) {
        month--;
      }
      myBuildDate = new GregorianCalendar(year, month, day);
    }

    Element logoElement = parentNode.getChild(ELEMENT_LOGO);
    if (logoElement != null) {
      myLogoUrl = logoElement.getAttributeValue(ATTRIBUTE_URL);
    }

    Element aboutLogoElement = parentNode.getChild(ELEMENT_ABOUT);
    if (aboutLogoElement != null) {
      myAboutLogoUrl = aboutLogoElement.getAttributeValue(ATTRIBUTE_URL);
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
      myWelcomeScreenCaptionUrl = welcomeScreen.getAttributeValue(CAPTION_URL_ATTR);
      myWelcomeScreenDeveloperSloganUrl = welcomeScreen.getAttributeValue(SLOGAN_URL_ATTR);
    }

    Element updateUrls = parentNode.getChild(UPDATE_URLS_ELEMENT_NAME);
    myUpdateUrls = new UpdateUrlsImpl(updateUrls);

    Element eapUpdateUrls = parentNode.getChild(EAP_UPDATE_URLS_ELEMENT_NAME);
    myEapUpdateUrls = new UpdateUrlsImpl(eapUpdateUrls);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    throw new WriteExternalException();
  }


  public String getComponentName() {
    return ApplicationNamesInfo.getComponentName();
  }

  private static class UpdateUrlsImpl implements UpdateUrls {
    private String myCheckingUrl;
    private String myDownloadUrl;
    @NonNls private static final String CHECK_ATTR = "check";
    @NonNls private static final String DOWNLOAD_ATTR = "download";

    public UpdateUrlsImpl(Element element) {
      if (element != null) {
        myCheckingUrl = element.getAttributeValue(CHECK_ATTR);
        myDownloadUrl = element.getAttributeValue(DOWNLOAD_ATTR);
      }
    }

    public String getCheckingUrl() {
      return myCheckingUrl;
    }

    public String getDownloadUrl() {
      return myDownloadUrl;
    }
  }
}
