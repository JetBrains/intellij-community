package com.intellij.json;

import com.intellij.CommonBundle;
import com.intellij.reference.SoftReference;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

import java.lang.ref.Reference;
import java.util.ResourceBundle;

/**
 * @author Mikhail Golubev
 */
public class JsonBundle {

  private static Reference<ResourceBundle> ourBundle;
  @NonNls private static final String BUNDLE = "com.intellij.json.JsonBundle";

  private JsonBundle() {
    // empty
  }

  public static String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, @NotNull Object... params) {
    return CommonBundle.message(getBundle(), key, params);
  }

  private static ResourceBundle getBundle() {
    ResourceBundle bundle = SoftReference.dereference(ourBundle);
    if (bundle == null) {
      bundle = ResourceBundle.getBundle(BUNDLE);
      ourBundle = new SoftReference<>(bundle);
    }
    return bundle;
  }
}
