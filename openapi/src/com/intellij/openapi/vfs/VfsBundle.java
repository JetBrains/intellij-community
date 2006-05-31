package com.intellij.openapi.vfs;

import com.intellij.CommonBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.PropertyKey;

import java.util.ResourceBundle;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 02.09.2005
 * Time: 17:46:54
 * To change this template use File | Settings | File Templates.
 */
public class VfsBundle {
  @NonNls private static final String BUNDLE = "messages.VfsBundle";

  private VfsBundle() {}

  public static String message(@PropertyKey(resourceBundle = BUNDLE) String key, Object... params) {
    return CommonBundle.message(ResourceBundle.getBundle(BUNDLE), key, params);
  }
}
