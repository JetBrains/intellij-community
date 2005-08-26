package com.intellij.ide;

import org.jetbrains.annotations.NonNls;

import java.util.ResourceBundle;

import com.intellij.CommonBundle;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 25.08.2005
 * Time: 19:03:38
 * To change this template use File | Settings | File Templates.
 */
public class IdeBundle {
  private static final ResourceBundle ourBundle = ResourceBundle.getBundle("com.intellij.ide.IdeBundle");

  private IdeBundle() {}

  public static String message(@NonNls  String key, Object... params) {
    return CommonBundle.message(ourBundle, key, params);
  }
}
