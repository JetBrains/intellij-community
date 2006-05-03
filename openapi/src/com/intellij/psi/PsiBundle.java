/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.psi;

import com.intellij.CommonBundle;
import org.jetbrains.annotations.PropertyKey;
import org.jetbrains.annotations.NonNls;

import java.util.ResourceBundle;

/**
 * @author max
 */
public class PsiBundle {
  @NonNls private static final ResourceBundle ourBundle = ResourceBundle.getBundle("messages.PsiBundle");

  private PsiBundle() {}

  public static String message(@PropertyKey(resourceBundle = "messages.PsiBundle") String key, Object... params) {
    return CommonBundle.message(ourBundle, key, params);
  }
}
