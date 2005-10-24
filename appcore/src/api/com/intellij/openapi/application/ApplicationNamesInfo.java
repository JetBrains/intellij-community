/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
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
