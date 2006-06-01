package com.intellij.openapi.keymap;

import com.intellij.CommonBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.PropertyKey;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.util.ResourceBundle;

/**
 * Created by IntelliJ IDEA.
 * User: Sergey.Grigorchuk
 * Date: 01.09.2005
 * Time: 17:13:10
 */
public class KeyMapBundle {
  private static Reference<ResourceBundle> ourBundle;

  @NonNls
  protected static final String PATH_TO_BUNDLE = "messages.KeyMapBundle";

  private KeyMapBundle() {
  }

  public static String message(@PropertyKey(resourceBundle = "messages.KeyMapBundle")String key, Object... params) {
    return CommonBundle.message(getBundle(), key, params);
  }

  private static ResourceBundle getBundle() {
    ResourceBundle bundle = null;
    if (ourBundle != null) bundle = ourBundle.get();
    if (bundle == null) {
      bundle = ResourceBundle.getBundle(PATH_TO_BUNDLE);
      ourBundle = new SoftReference<ResourceBundle>(bundle);
    }
    return bundle;
  }
}
