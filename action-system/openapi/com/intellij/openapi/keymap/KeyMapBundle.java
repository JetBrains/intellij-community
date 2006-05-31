package com.intellij.openapi.keymap;

import com.intellij.CommonBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.PropertyKey;

import java.util.ResourceBundle;

/**
 * Created by IntelliJ IDEA.
 * User: Sergey.Grigorchuk
 * Date: 01.09.2005
 * Time: 17:13:10
 */
public class KeyMapBundle {
  @NonNls
  protected static final String PATH_TO_BUNDLE = "messages.KeyMapBundle";

  private KeyMapBundle() {
  }

  public static String message(@PropertyKey(resourceBundle = "messages.KeyMapBundle") String key, Object... params) {
    return CommonBundle.message(ResourceBundle.getBundle(PATH_TO_BUNDLE), key, params);
  }

}
