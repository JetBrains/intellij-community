/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.ant;

import com.intellij.CommonBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.PropertyKey;

import java.util.ResourceBundle;

public class AntBundle {
  @NonNls
  protected static final String PATH_TO_BUNDLE = "messages.AntBundle";
  private final static ResourceBundle ourResourceBundle = ResourceBundle.getBundle(PATH_TO_BUNDLE);

  private AntBundle() {
  }

  public static String message(@PropertyKey(resourceBundle = "messages.AntBundle") String key, Object... params) {
    return CommonBundle.message(ourResourceBundle, key, params);
  }

}
