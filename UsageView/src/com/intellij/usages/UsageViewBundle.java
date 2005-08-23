package com.intellij.usages;

import com.intellij.CommonBundle;

import java.util.ResourceBundle;
import java.util.MissingResourceException;
import java.text.MessageFormat;

import org.jetbrains.annotations.NonNls;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 11.08.2005
 * Time: 16:04:31
 * To change this template use File | Settings | File Templates.
 */
public class UsageViewBundle {
  private static final ResourceBundle ourBundle = ResourceBundle.getBundle("com.intellij.usages.UsageView");

  private UsageViewBundle() {}

  public static String message(@NonNls String key, Object... params) {
    return CommonBundle.message(ourBundle, key, params);
  }
}
