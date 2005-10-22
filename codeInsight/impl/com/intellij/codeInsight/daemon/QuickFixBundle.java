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
public class QuickFixBundle {
  @NonNls private static final ResourceBundle ourBundle = ResourceBundle.getBundle("messages.QuickFixBundle");

  private QuickFixBundle() {}

  public static String message(@NonNls @PropertyKey(resourceBundle = "messages.QuickFixBundle") String key, Object... params) {
    return CommonBundle.message(ourBundle, key, params);
  }
}
