/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

/**
 * Specifies how method names are converted into XML element names
 *
 * @author peter
 */
public abstract class DomNameStrategy {

  /**
   * @param propertyName property name, i.e. method name without first 'get', 'set' or 'is'
   * @return XML element name
   */
  public abstract String convertName(String propertyName);

  /**
   * Is used to get presentable DOM elements in UI  
   * @param xmlElementName XML element name
   * @return Presentable DOM element name
   */
  public abstract String splitIntoWords(final String xmlElementName);

  /**
   * This strategy splits property name into words, decapitalizes them and joins using hyphen as separator,
   * e.g. getXmlElementName() will correspond to xml-element-name
   */
  public static final DomNameStrategy HYPHEN_STRATEGY = new HyphenNameStrategy();
  /**
   * This strategy decapitalizes property name, e.g. getXmlElementName() will correspond to xmlElementName
   */
  public static final DomNameStrategy JAVA_STRATEGY = new JavaNameStrategy();
}
