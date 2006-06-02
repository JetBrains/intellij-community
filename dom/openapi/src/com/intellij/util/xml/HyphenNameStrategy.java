/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.openapi.util.text.StringUtil;

/**
 * @author peter
 */
public class HyphenNameStrategy extends DomNameStrategy {
  public String convertName(String propertyName) {
    final String[] words = NameUtil.nameToWords(propertyName);
    for (int i = 0; i < words.length; i++) {
      words[i] = StringUtil.decapitalize(words[i]);
    }
    return StringUtil.join(words, "-");
  }

  public String splitIntoWords(final String tagName) {
    return tagName.replace('-', ' ');
  }
}
