/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.application;

import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.PlatformUtils;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class ApplicationNamesInfo {
  @NonNls private static final String COMPONENT_NAME = "ApplicationInfo";
  @NonNls private static final String ELEMENT_NAMES = "names";
  @NonNls private static final String ATTRIBUTE_PRODUCT = "product";
  @NonNls private static final String ATTRIBUTE_FULL_NAME = "fullname";
  @NonNls private static final String ATTRIBUTE_SCRIPT = "script";
  @NonNls private static final String ELEMENT_VERSION = "version";
  @NonNls private static final String ATTRIBUTE_MAJOR = "major";
  @NonNls private static final String ATTRIBUTE_MINOR = "minor";

  private String myProductName;
  private String myFullProductName;
  private String myLowercaseProductName;
  private String myScriptName;
  private String myMajorVersion;
  private String myMinorVersion;

  private static class ApplicationNamesInfoHolder {
    private static final ApplicationNamesInfo ourInstance = new ApplicationNamesInfo();
    private ApplicationNamesInfoHolder() { }
  }

  @NotNull
  public static ApplicationNamesInfo getInstance() {
    return ApplicationNamesInfoHolder.ourInstance;
  }

  private ApplicationNamesInfo() {
    try {
      //noinspection HardCodedStringLiteral
      readInfo((JDOMUtil.load(ApplicationNamesInfo.class.getResourceAsStream("/idea/" + getComponentName() + ".xml"))));
    }
    catch (Exception e) {
      //noinspection CallToPrintStackTrace
      e.printStackTrace();
    }
  }

  private void readInfo(final Element rootElement) {
    final Element names = rootElement.getChild(ELEMENT_NAMES);
    myProductName = names.getAttributeValue(ATTRIBUTE_PRODUCT);
    myFullProductName = names.getAttributeValue(ATTRIBUTE_FULL_NAME);
    myLowercaseProductName = StringUtil.capitalize(myProductName.toLowerCase());
    myScriptName = names.getAttributeValue(ATTRIBUTE_SCRIPT);

    final Element version = rootElement.getChild(ELEMENT_VERSION);
    if (version != null) {
      myMajorVersion = version.getAttributeValue(ATTRIBUTE_MAJOR);
      myMinorVersion = version.getAttributeValue(ATTRIBUTE_MINOR);
    }
  }

  /**
   * @return "IDEA"
   */
  public String getProductName() {
    return myProductName;
  }

  /**
   * @return "IntelliJ IDEA"
   */
  public String getFullProductName() {
    return myFullProductName;
  }

  /**
   * @return "Idea"
   */
  public String getLowercaseProductName() {
    return myLowercaseProductName;
  }

  /**
   * @return "idea"
   */
  public String getScriptName() {
    return myScriptName;
  }

  public String getMinorVersion() {
    return myMinorVersion;
  }

  public String getMajorVersion() {
    return myMajorVersion;
  }

  public static String getComponentName() {
    final String prefix = System.getProperty(PlatformUtils.PLATFORM_PREFIX_KEY);
    if (prefix != null) {
      return prefix + COMPONENT_NAME;
    }
    return COMPONENT_NAME;
  }
}
