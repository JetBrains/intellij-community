/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.xml;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.PropertyKey;

import java.util.ResourceBundle;

import com.intellij.CommonBundle;

/**
 * Created by IntelliJ IDEA.
 * User: lesya
 * Date: Aug 31, 2005
 * Time: 3:43:13 PM
 * To change this template use File | Settings | File Templates.
 */
public class XmlBundle {
  @NonNls
  protected static final String PATH_TO_BUNDLE = "com.intellij.xml.XmlBundle";
  private final static ResourceBundle ourResourceBundle = ResourceBundle.getBundle(PATH_TO_BUNDLE);

  public static String message(@PropertyKey(resourceBundle = "com.intellij.xml.XmlBundle") String key, Object... params) {
    return CommonBundle.message(ourResourceBundle, key, params);
  }

}
