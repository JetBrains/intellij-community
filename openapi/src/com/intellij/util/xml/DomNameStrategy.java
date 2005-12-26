/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

/**
 * @author peter
 */
public interface DomNameStrategy {
  String convertName(String propertyName);
  String splitIntoWords(final String tagName);

  DomNameStrategy HYPHEN_STRATEGY = new HyphenNameStrategy();
  DomNameStrategy JAVA_STRATEGY = new JavaNameStrategy();
}
