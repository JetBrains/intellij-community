/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.openapi.diff;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.PropertyKey;
import com.intellij.CommonBundle;

import java.util.ResourceBundle;

/**
 * Created by IntelliJ IDEA.
 * User: lesya
 * Date: Aug 31, 2005
 * Time: 6:02:17 PM
 * To change this template use File | Settings | File Templates.
 */
public class DiffBundle {
  @NonNls protected static final String PATH_TO_BUNDLE = "com.intellij.openapi.diff.DiffBundle";
  private final static ResourceBundle ourResourceBundle = ResourceBundle.getBundle(PATH_TO_BUNDLE);

  public static String message(@PropertyKey(resourceBundle = "com.intellij.openapi.diff.DiffBundle") String key, Object... params) {
    return CommonBundle.message(ourResourceBundle, key, params);
  }

}
