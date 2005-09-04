/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.ant;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.PropertyKey;

import java.util.ResourceBundle;

import com.intellij.CommonBundle;

public class AntBundle {
  @NonNls
  protected static final String PATH_TO_BUNDLE = "com.intellij.ant.AntBundle";
  private final static ResourceBundle ourResourceBundle = ResourceBundle.getBundle(PATH_TO_BUNDLE);

  public static String message(@PropertyKey(resourceBundle = "com.intellij.ant.AntBundle") String key, Object... params) {
    return CommonBundle.message(ourResourceBundle, key, params);
  }

}
