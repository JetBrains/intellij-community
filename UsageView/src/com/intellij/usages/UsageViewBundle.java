package com.intellij.usages;

import java.util.ResourceBundle;
import java.util.MissingResourceException;
import java.text.MessageFormat;

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

  public static String message(String key, Object... params) {
    String value;
    try {
      value = ourBundle.getString(key);
    }
    catch (MissingResourceException e) {
      return "!" + key + "!";
    }

    if (params.length > 0) {
      return MessageFormat.format(value, params);
    }

    return value;
  }
}
