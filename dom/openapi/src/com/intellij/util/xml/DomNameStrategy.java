/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

/**
 * @author peter
 */
public abstract class DomNameStrategy {
  public abstract String convertName(String propertyName);

  /**
   * Is used to get present DOM elements in UI  
   * @param tagName
   * @return
   */
  public abstract String splitIntoWords(final String tagName);

  public static final DomNameStrategy HYPHEN_STRATEGY = new HyphenNameStrategy();
  public static final DomNameStrategy JAVA_STRATEGY = new JavaNameStrategy();
}
