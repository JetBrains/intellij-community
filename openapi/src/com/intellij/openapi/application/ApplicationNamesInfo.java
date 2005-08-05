/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.application;

import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.jdom.Document;
import org.jdom.Element;

/**
 * @author nik
 */
public class ApplicationNamesInfo {
  public static final String COMPONENT_NAME = "ApplicationInfo";
  private static ApplicationNamesInfo ourInstance;
  private String myProductName;
  private String myFullProductName;
  private String myLowercaseProductName;

  public static ApplicationNamesInfo getInstance() {
    if (ourInstance == null) {
      ourInstance = new ApplicationNamesInfo();
    }
    return ourInstance;
  }

  private ApplicationNamesInfo() {
    try {
      Document doc = JDOMUtil.loadDocument(ApplicationNamesInfo.class.getResourceAsStream("/idea/" + COMPONENT_NAME + ".xml"));
      readInfo(doc.getRootElement());
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }

  private void readInfo(final Element rootElement) {
    final Element names = rootElement.getChild("names");
    myProductName = names.getAttributeValue("product");
    myFullProductName = names.getAttributeValue("fullname");
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
