/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.codeInsight.daemon;

import com.intellij.CommonBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.PropertyKey;

import java.util.ResourceBundle;

/**
 * @author max
 */
public class JavaErrorMessages {
  private static ResourceBundle ourBundle;

  @NonNls private static final String BUNDLE = "messages.JavaErrorMessages";

  private JavaErrorMessages() {
  }

  public static String message(@PropertyKey(resourceBundle = BUNDLE)String key, Object... params) {
    return CommonBundle.message(getBundle(), key, params);
  }

  private static ResourceBundle getBundle() {
    if (ourBundle == null) {
      ourBundle = ResourceBundle.getBundle(BUNDLE);
    }
    return ourBundle;
  }
}
