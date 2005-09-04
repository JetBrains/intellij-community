/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.openapi.localVcs;

import com.intellij.CommonBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.PropertyKey;

import java.util.ResourceBundle;

/**
 * Created by IntelliJ IDEA.
 * User: lesya
 * Date: Aug 29, 2005
 * Time: 10:39:07 PM
 * To change this template use File | Settings | File Templates.
 */
public class LocalVcsBundle {
  @NonNls protected static final String PATH_TO_BUNDLE = "com.intellij.openapi.localVcs.LocalVcsBundle";
  private final static ResourceBundle ourResourceBundle = ResourceBundle.getBundle(PATH_TO_BUNDLE);

  public static String message(@PropertyKey(resourceBundle = "com.intellij.openapi.localVcs.LocalVcsBundle") String key, Object... params) {
    return CommonBundle.message(ourResourceBundle, key, params);
  }

}
