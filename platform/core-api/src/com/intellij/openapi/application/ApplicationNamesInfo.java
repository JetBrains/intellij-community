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
package com.intellij.openapi.application;

import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.PlatformUtils;
import org.jdom.Document;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * @author nik
 */
public class ApplicationNamesInfo {
  private static final String COMPONENT_NAME = "ApplicationInfo";
  private static final String ELEMENT_NAMES = "names";
  private static final String ATTRIBUTE_PRODUCT = "product";
  private static final String ATTRIBUTE_FULL_NAME = "fullname";
  private static final String ATTRIBUTE_SCRIPT = "script";

  private String myProductName;
  private String myFullProductName;
  private String myLowercaseProductName;
  private String myScriptName;

  private static class ApplicationNamesInfoHolder {
    private static final ApplicationNamesInfo ourInstance = new ApplicationNamesInfo();
    private ApplicationNamesInfoHolder() { }
  }

  @NotNull
  public static ApplicationNamesInfo getInstance() {
    return ApplicationNamesInfoHolder.ourInstance;
  }

  private ApplicationNamesInfo() {
    String resource = "/idea/" + getComponentName() + ".xml";
    try {
      Document doc = JDOMUtil.loadDocument(ApplicationNamesInfo.class, resource);
      readInfo(doc.getRootElement());
    }
    catch (Exception e) {
      throw new RuntimeException("Cannot load resource: " + resource, e);
    }
  }

  private void readInfo(final Element rootElement) {
    final Element names = rootElement.getChild(ELEMENT_NAMES);
    myProductName = names.getAttributeValue(ATTRIBUTE_PRODUCT);
    myFullProductName = names.getAttributeValue(ATTRIBUTE_FULL_NAME);
    myLowercaseProductName = StringUtil.capitalize(myProductName.toLowerCase(Locale.US));
    myScriptName = names.getAttributeValue(ATTRIBUTE_SCRIPT);
  }

  /**
   * <strong>Consider using {@link #getFullProductName()} instead.</strong> For the most of the products the both methods return the same value.
   * @return shortened name of the product if it contains two words (e.g. {@code "IDEA"} for IntelliJ IDEA, {@code "WebStorm"} for WebStorm)
   */
  public String getProductName() {
    return myProductName;
  }

  /**
   * @return name of the product without vendor name (e.g. {@code "IntelliJ IDEA"} for IntelliJ IDEA, {@code "WebStorm"} for WebStorm)
   */
  public String getFullProductName() {
    return myFullProductName;
  }

  /**
   * <strong>Consider using {@link #getFullProductName()} instead.</strong> This method is kept mostly for historical reasons, it is used to
   * name the default directory for newly created projects ('Idea Project' for IntelliJ IDEA, for example)
   * @return name of the product with lowercased all letters except the first one (e.g. {@code "Idea"} for IntelliJ IDEA, {@code "Webstorm"} for WebStorm)
   */
  public String getLowercaseProductName() {
    return myLowercaseProductName;
  }

  /**
   * @return base name of the script files (*.exe, *.bat, *.sh) from the product 'bin' directory without extension ({@code "idea"} for IntelliJ IDEA, {@code "webstorm"} for WebStorm)
   */
  public String getScriptName() {
    return myScriptName;
  }

  public static String getComponentName() {
    String prefix = System.getProperty(PlatformUtils.PLATFORM_PREFIX_KEY);
    return prefix != null ? prefix + COMPONENT_NAME : COMPONENT_NAME;
  }
}
