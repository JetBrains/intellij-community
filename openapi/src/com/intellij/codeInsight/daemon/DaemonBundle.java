/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.codeInsight.daemon;

import com.intellij.CommonBundle;
import org.jetbrains.annotations.PropertyKey;

import java.util.ResourceBundle;

/**
 * @author max
 */
public class DaemonBundle {
  private static final ResourceBundle ourBundle = ResourceBundle.getBundle("com.intellij.codeInsight.daemon.DaemonBundle");

  private DaemonBundle() {}

  public static String message(@PropertyKey(resourceBundle = "com.intellij.codeInsight.daemon.DaemonBundle") String key, Object... params) {
    return CommonBundle.message(ourBundle, key, params);
  }
}
