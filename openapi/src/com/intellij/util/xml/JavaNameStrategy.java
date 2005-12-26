/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.util.Function;

import java.util.Arrays;

/**
 * @author peter
 */
public class JavaNameStrategy implements DomNameStrategy {
  private static final Function<String,String> DECAPITALIZE_FUNCTION = new Function<String, String>() {
    public String fun(final String s) {
      return StringUtil.decapitalize(s);
    }
  };

  public final String convertName(String propertyName) {
    return StringUtil.decapitalize(propertyName);
  }

  public final String splitIntoWords(final String tagName) {
    return StringUtil.join(Arrays.asList(NameUtil.nameToWords(tagName)), DECAPITALIZE_FUNCTION, " ");
  }
}
