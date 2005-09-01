/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.ui;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.PropertyKey;
import com.intellij.CommonBundle;

import java.util.ResourceBundle;

/**
 * Created by IntelliJ IDEA.
 * User: lesya
 * Date: Sep 1, 2005
 * Time: 7:09:26 PM
 * To change this template use File | Settings | File Templates.
 */
public class UIBundle {
  @NonNls protected static final String PATH_TO_BUNDLE = "com.intellij.ui.UIBundle";
  private final static ResourceBundle ourResourceBundle = ResourceBundle.getBundle(PATH_TO_BUNDLE);

  public static String message(@PropertyKey String key, Object... params) {
    return CommonBundle.message(ourResourceBundle, key, params);
  }

}
