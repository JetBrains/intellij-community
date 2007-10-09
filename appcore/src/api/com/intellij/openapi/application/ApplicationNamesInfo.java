/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
import org.jdom.Document;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

/**
 * @author nik
 */
public class ApplicationNamesInfo {
  @NonNls
  public static final String COMPONENT_NAME = "ApplicationInfo";
  private static ApplicationNamesInfo ourInstance;
  private String myProductName;
  private String myFullProductName;
  private String myLowercaseProductName;
  @NonNls private static final String ELEMENT_NAMES = "names";
  @NonNls private static final String ATTRIBUTE_PRODUCT = "product";
  @NonNls private static final String ATTRIBUTE_FULLNAME = "fullname";

  public static ApplicationNamesInfo getInstance() {
    if (ourInstance == null) {
      ourInstance = new ApplicationNamesInfo();
    }
    return ourInstance;
  }

  private ApplicationNamesInfo() {
    try {
      //noinspection HardCodedStringLiteral
      Document doc = JDOMUtil.loadDocument(ApplicationNamesInfo.class.getResourceAsStream("/idea/" + COMPONENT_NAME + ".xml"));
      readInfo(doc.getRootElement());
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }

  private void readInfo(final Element rootElement) {
    final Element names = rootElement.getChild(ELEMENT_NAMES);
    myProductName = names.getAttributeValue(ATTRIBUTE_PRODUCT);
    myFullProductName = names.getAttributeValue(ATTRIBUTE_FULLNAME);
    myLowercaseProductName = StringUtil.capitalize(myProductName.toLowerCase());
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
}
