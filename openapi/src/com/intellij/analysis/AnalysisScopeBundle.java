/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.analysis;

import com.intellij.CommonBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.PropertyKey;

import java.util.ResourceBundle;

/**
 * @author max
 */
public class AnalysisScopeBundle {
  @NonNls private static final ResourceBundle ourBundle = ResourceBundle.getBundle("messages.AnalysisScopeBundle");

  private AnalysisScopeBundle() {
  }

  public static String message(@PropertyKey(resourceBundle = "messages.AnalysisScopeBundle") String key, Object... params) {
    return CommonBundle.message(ourBundle, key, params);
  }
}
