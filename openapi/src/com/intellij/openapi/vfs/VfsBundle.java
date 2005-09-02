package com.intellij.openapi.vfs;

import org.jetbrains.annotations.PropertyKey;

import java.util.ResourceBundle;

import com.intellij.CommonBundle;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 02.09.2005
 * Time: 17:46:54
 * To change this template use File | Settings | File Templates.
 */
public class VfsBundle {
  private static final ResourceBundle ourBundle = ResourceBundle.getBundle("com.intellij.openapi.vfs.VfsBundle");

  private VfsBundle() {}

  public static String message(@PropertyKey String key, Object... params) {
    return CommonBundle.message(ourBundle, key, params);
  }
}
