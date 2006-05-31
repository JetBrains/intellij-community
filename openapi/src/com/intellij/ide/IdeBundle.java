package com.intellij.ide;

import com.intellij.CommonBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.PropertyKey;

import java.util.ResourceBundle;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 25.08.2005
 * Time: 19:03:38
 * To change this template use File | Settings | File Templates.
 */
public class IdeBundle {
  @NonNls private static final String BUNDLE = "messages.IdeBundle";

  private IdeBundle() {}

  public static String message(@PropertyKey(resourceBundle = BUNDLE) String key, Object... params) {
    return CommonBundle.message(ResourceBundle.getBundle(BUNDLE), key, params);
  }
}
