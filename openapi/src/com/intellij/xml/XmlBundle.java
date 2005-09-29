/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.xml;

import com.intellij.CommonBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.PropertyKey;

import java.util.ResourceBundle;

/**
 * Created by IntelliJ IDEA.
 * User: lesya
 * Date: Aug 31, 2005
 * Time: 3:43:13 PM
 * To change this template use File | Settings | File Templates.
 */
public class XmlBundle {
  @NonNls protected static final String PATH_TO_BUNDLE = "messages.XmlBundle";
  private final static ResourceBundle ourResourceBundle = ResourceBundle.getBundle(PATH_TO_BUNDLE);

  private XmlBundle() {
  }

  public static String message(@PropertyKey(resourceBundle = "messages.XmlBundle") String key, Object... params) {
    return CommonBundle.message(ourResourceBundle, key, params);
  }

}
