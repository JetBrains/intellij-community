package com.intellij.ide;

import com.intellij.CommonBundle;
import org.jetbrains.annotations.PropertyKey;
import org.jetbrains.annotations.NonNls;

import java.util.ResourceBundle;

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

  public static String message(@NonNls @PropertyKey(resourceBundle = "com.intellij.ide.IdeBundle") String key, Object... params) {
    return CommonBundle.message(ourBundle, key, params);
  }
}
