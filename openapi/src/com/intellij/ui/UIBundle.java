/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.ui;

import com.intellij.CommonBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.PropertyKey;

import java.util.ResourceBundle;

/**
 * Created by IntelliJ IDEA.
 * User: lesya
 * Date: Sep 1, 2005
 * Time: 7:09:26 PM
 * To change this template use File | Settings | File Templates.
 */
public class UIBundle {
  @NonNls protected static final String PATH_TO_BUNDLE = "messages.UIBundle";
  private final static ResourceBundle ourResourceBundle = ResourceBundle.getBundle(PATH_TO_BUNDLE);

  private UIBundle() {
  }

  public static String message(@PropertyKey(resourceBundle = "messages.UIBundle") String key, Object... params) {
    return CommonBundle.message(ourResourceBundle, key, params);
  }

}
