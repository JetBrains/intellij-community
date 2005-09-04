/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.openapi.options;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.PropertyKey;
import com.intellij.CommonBundle;

import java.util.ResourceBundle;

/**
 * Created by IntelliJ IDEA.
 * User: lesya
 * Date: Sep 1, 2005
 * Time: 6:05:22 PM
 * To change this template use File | Settings | File Templates.
 */
public class OptionsBundle {
  @NonNls protected static final String PATH_TO_BUNDLE = "com.intellij.openapi.options.OptionsBundle";
  private final static ResourceBundle ourResourceBundle = ResourceBundle.getBundle(PATH_TO_BUNDLE);

  public static String message(@PropertyKey(resourceBundle = "com.intellij.openapi.options.OptionsBundle") String key, Object... params) {
    return CommonBundle.message(ourResourceBundle, key, params);
  }

}
