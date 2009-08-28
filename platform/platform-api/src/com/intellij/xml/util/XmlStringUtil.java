/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.xml.util;

/**
 * @author yole
 */
public class XmlStringUtil {
  private XmlStringUtil() {
  }

  public static String escapeString(String str) {
    return escapeString(str, false);
  }

  public static String escapeString(String str, final boolean escapeWhiteSpace) {
    return XmlTagUtilBase.escapeString(str, escapeWhiteSpace);
  }
}