/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.ant;

import org.jetbrains.annotations.NonNls;

import java.util.ResourceBundle;

import com.intellij.CommonBundle;

public class AntBundle {
  @NonNls
  protected static final String PATH_TO_BUNDLE = "com.intellij.ant.AntBundle";
  private final static ResourceBundle ourResourceBundle = ResourceBundle.getBundle(PATH_TO_BUNDLE);

  public static String message(@NonNls String key, Object... params) {
    return CommonBundle.message(ourResourceBundle, key, params);
  }

}
