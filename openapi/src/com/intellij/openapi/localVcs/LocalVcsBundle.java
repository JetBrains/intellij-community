/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.openapi.localVcs;

import org.jetbrains.annotations.NonNls;

import java.util.ResourceBundle;

import com.intellij.CommonBundle;

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

  public static String message(@NonNls String key, Object... params) {
    return CommonBundle.message(ourResourceBundle, key, params);
  }

}
