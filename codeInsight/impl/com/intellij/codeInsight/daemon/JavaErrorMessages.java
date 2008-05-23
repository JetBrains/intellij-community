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

  @NonNls private static final String BUNDLE = "messages.JavaErrorMessages";

  private JavaErrorMessages() {
  }

  public static String message(@PropertyKey(resourceBundle = BUNDLE)String key, Object... params) {
    return CommonBundle.message(getBundle(), key, params);
  }

  private static class ResourceBundleHolder {
    private static final ResourceBundle ourBundle = ResourceBundle.getBundle(BUNDLE);
  }

  private static ResourceBundle getBundle() {
    return ResourceBundleHolder.ourBundle;
  }
}
